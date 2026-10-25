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
import me.yakar.muster.internal.rule.Step;
import me.yakar.muster.rule.RuleResult;
import me.yakar.muster.rule.Violation;
import org.jspecify.annotations.Nullable;

/**
 * The rules that check inputs of one type, prepared once for repeated use. Get one from {@link
 * Muster#chain} and keep it.
 *
 * <p>Rules run in the order they are listed. Before each one, the rules it {@linkplain
 * me.yakar.muster.rule.RequiresRule requires} that have not run yet run first, each after its own
 * requirements: listed ones in list order, the others in the order of their ids. The order is the
 * same on every call, and in {@link CheckMode#FAIL_FAST} mode, which stops at the first violated
 * rule, it decides which violation is reported, so list cheap rules first.
 *
 * <p>A chain checks in the mode of the {@code Muster} that created it, unless {@link #withMode}
 * gives it its own. Chains are immutable and thread-safe.
 *
 * @param <I> the type of input
 */
public final class RuleChain<I> {

  private final Class<?> inputType;
  private final List<Step> steps;
  private final Supplier<? extends @Nullable Principal> caller;
  private final Supplier<Locale> locale;
  private final CheckMode mode;

  RuleChain(
      Class<?> inputType,
      List<Step> steps,
      Supplier<? extends @Nullable Principal> caller,
      Supplier<Locale> locale,
      CheckMode mode) {
    this.inputType = inputType;
    this.steps = List.copyOf(steps);
    this.caller = caller;
    this.locale = locale;
    this.mode = mode;
  }

  /**
   * Returns a chain with the same rules that checks in the given mode, whatever the mode of the
   * {@code Muster}. This chain is not changed.
   *
   * @param mode the mode
   * @return the chain with the given mode
   */
  public RuleChain<I> withMode(CheckMode mode) {
    return new RuleChain<>(inputType, steps, caller, locale, requireNonNull(mode, "mode"));
  }

  /**
   * Returns the mode this chain checks in: its own, or else the mode of the {@code Muster} that
   * created it.
   *
   * @return the mode
   */
  public CheckMode mode() {
    return mode;
  }

  /**
   * Checks an input against the rules that apply to the call, in the mode of this chain. A rule is
   * skipped if a rule it requires did not pass. An exception thrown by a rule propagates; it is not
   * a violation.
   *
   * @param input the input
   * @throws BusinessRuleViolationException if a rule is violated
   * @throws IllegalArgumentException if {@code input} is not of the chain's type, which only
   *     unchecked code can pass
   */
  public void check(I input) {
    requireNonNull(input, "input");
    if (!inputType.isInstance(input)) {
      throw new IllegalArgumentException(
          "the chain checks "
              + inputType.getTypeName()
              + ", not "
              + input.getClass().getTypeName());
    }
    run(input, mode);
  }

  /** Checks an input that the caller knows to be of the chain's type, in the given mode. */
  void run(Object input, CheckMode mode) {
    var callCaller = caller.get();
    var callLocale = requireNonNull(locale.get(), "the locale supplier returned null");
    // Created on the first failure, so that a passing check allocates no list.
    ArrayList<Violation> found = null;
    var passed = new boolean[steps.size()];
    for (var i = 0; i < steps.size(); i++) {
      var step = steps.get(i);
      if (!allPassed(step.requires(), passed)) {
        continue;
      }
      var rule = step.rule();
      // From the context prepared with the rule, so that the rule id is not checked again.
      var context = rule.context().toBuilder().locale(callLocale);
      if (callCaller != null) {
        context.caller(callCaller);
      }
      var ctx = context.build();
      if (!rule.instance().appliesTo(ctx)) {
        continue;
      }
      var result = rule.instance().check(input, ctx);
      if (result == null) {
        throw new NullPointerException("rule '" + rule.id() + "' returned null");
      }
      if (result instanceof RuleResult.Failed failed) {
        var violations = failed.violations();
        for (var violation : violations) {
          if (!violation.ruleId().equals(rule.id())) {
            throw new IllegalStateException(
                "rule '"
                    + rule.id()
                    + "' reported a violation of rule '"
                    + violation.ruleId()
                    + "'; build violations from the RuleContext the rule receives");
          }
        }
        if (mode == CheckMode.FAIL_FAST) {
          // Nothing was found before; the exception keeps the rule's unmodifiable list as it is.
          throw new BusinessRuleViolationException(violations);
        }
        if (found == null) {
          found = new ArrayList<>();
        }
        found.addAll(violations);
      } else {
        passed[i] = true;
      }
    }
    if (found != null) {
      throw new BusinessRuleViolationException(found);
    }
  }

  @Override
  public String toString() {
    return "RuleChain[input="
        + inputType.getTypeName()
        + ", mode="
        + mode
        + ", rules="
        + ruleIds()
        + "]";
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
}
