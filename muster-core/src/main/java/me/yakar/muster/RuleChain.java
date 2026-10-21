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
 * <p>A rule runs after the rules it {@linkplain me.yakar.muster.rule.RequiresRule requires};
 * otherwise rules run in the order of their ids, so the violations come in the same order on every
 * call. Muster has no notion of cost: in {@link CheckMode#FAIL_FAST} mode, the order alone decides
 * which violation is reported.
 *
 * <p>A chain checks in the {@linkplain #mode() mode} of the Muster that created it, unless {@link
 * #withMode} gives it its own.
 *
 * @param <I> the type of input
 */
public final class RuleChain<I> {

  private final List<Step> steps;
  private final Supplier<? extends @Nullable Principal> caller;
  private final Supplier<Locale> locale;
  private final CheckMode mode;

  RuleChain(
      List<Step> steps,
      Supplier<? extends @Nullable Principal> caller,
      Supplier<Locale> locale,
      CheckMode mode) {
    this.steps = List.copyOf(steps);
    this.caller = caller;
    this.locale = locale;
    this.mode = mode;
  }

  /**
   * Returns a chain with the same rules that checks in the given mode, whatever the mode of the
   * Muster. This chain is not changed.
   *
   * @param mode the mode
   * @return the chain with the given mode
   */
  public RuleChain<I> withMode(CheckMode mode) {
    return new RuleChain<>(steps, caller, locale, requireNonNull(mode, "mode"));
  }

  /**
   * Returns the mode this chain checks in: its own, or else the mode of the Muster that created it.
   *
   * @return the mode
   */
  public CheckMode mode() {
    return mode;
  }

  /**
   * Checks an input against the rules that apply to the call. In {@link CheckMode#FAIL_FAST} mode
   * the check stops at the first violated rule; in {@link CheckMode#COLLECT_ALL} mode it collects
   * the violations of every rule. A rule whose required rules did not all pass is skipped.
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
    var passed = new boolean[steps.size()];
    for (var i = 0; i < steps.size(); i++) {
      var step = steps.get(i);
      if (!allPassed(step.requires(), passed)) {
        continue;
      }
      var rule = step.rule();
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
        if (mode == CheckMode.FAIL_FAST) {
          break;
        }
      } else {
        passed[i] = true;
      }
    }
    if (!violations.isEmpty()) {
      throw new BusinessRuleViolationException(violations);
    }
  }

  /** Returns the ids of the rules in the chain, in the order they run. */
  List<String> ruleIds() {
    return steps.stream().map(step -> step.rule().id()).toList();
  }

  private static boolean allPassed(int[] required, boolean[] passed) {
    for (var index : required) {
      if (!passed[index]) {
        return false;
      }
    }
    return true;
  }

  /**
   * A rule in a chain.
   *
   * @param rule the rule
   * @param requires the positions in the chain of the rules it requires, all before its own
   */
  record Step(PreparedRule rule, int[] requires) {}
}
