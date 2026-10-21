/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.yakar.muster;

import static java.util.Objects.requireNonNull;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import me.yakar.muster.internal.PreparedRule;
import me.yakar.muster.rule.RuleContext;
import me.yakar.muster.rule.RuleResult;
import me.yakar.muster.rule.Violation;
import org.jspecify.annotations.Nullable;

/**
 * The rules an input is checked against, prepared for repeated use. Get one from {@link
 * Muster#chain}.
 *
 * <p>The rules run in the order of their ids, so the violations come in the same order on every
 * call.
 *
 * @param <I> the type of input
 */
public final class RuleChain<I> {

  private final List<PreparedRule> rules;
  private final Supplier<? extends @Nullable Principal> caller;
  private final Supplier<Locale> locale;

  RuleChain(
      List<PreparedRule> rules,
      Supplier<? extends @Nullable Principal> caller,
      Supplier<Locale> locale) {
    this.rules = List.copyOf(rules);
    this.caller = caller;
    this.locale = locale;
  }

  /**
   * Checks an input against every rule that applies to the call, and collects all violations.
   *
   * <p>A rule that fails with an exception is not a violation: the exception propagates.
   *
   * @param input the input
   * @throws BusinessRuleViolationException if a rule is violated
   * @throws NullPointerException if {@code input} is {@code null}, or the locale supplier or a rule
   *     returns {@code null}
   */
  public void check(I input) {
    requireNonNull(input, "input");
    var callCaller = caller.get();
    var callLocale = requireNonNull(locale.get(), "the locale supplier returned null");
    var violations = new ArrayList<Violation>();
    for (var rule : rules) {
      var context = RuleContext.builder(rule.id()).locale(callLocale);
      if (callCaller != null) {
        context.caller(callCaller);
      }
      var ctx = context.build();
      if (!rule.rule().appliesTo(ctx)) {
        continue;
      }
      var result = rule.rule().check(input, ctx);
      if (result == null) {
        throw new NullPointerException("rule '" + rule.id() + "' returned null");
      }
      if (result instanceof RuleResult.Failed failed) {
        violations.addAll(failed.violations());
      }
    }
    if (!violations.isEmpty()) {
      throw new BusinessRuleViolationException(violations);
    }
  }
}
