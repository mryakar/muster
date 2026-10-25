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
import me.yakar.muster.internal.rule.PreparedRule;
import me.yakar.muster.internal.rule.Step;
import me.yakar.muster.rule.RuleContext;
import me.yakar.muster.rule.RuleResult;
import me.yakar.muster.rule.Violation;
import org.jspecify.annotations.Nullable;

/**
 * The rules an input is checked against, prepared for repeated use. Get one from {@link
 * Muster#chain}.
 *
 * <p>A rule runs after the rules it {@linkplain me.yakar.muster.rule.RequiresRule requires}. Muster
 * runs the listed rules in the order they are listed, and before each one the rules it requires
 * that have not run yet, each after its own requirements, ordered the same way: listed ones in the
 * order they are listed, the others in the order of their ids. A {@link Binding} runs the rules it
 * binds by type, and that no listed rule requires, after them, in the order of their ids. {@link
 * Muster#check}, which lists no rules, runs rules that do not depend on each other in the order of
 * their ids. Either way the violations come in the same order on every call. Muster has no notion
 * of cost: in {@link CheckMode#FAIL_FAST} mode, the order alone decides which violation is
 * reported, so list cheap rules first.
 *
 * <p>A chain checks in the {@linkplain #mode() mode} of the Muster that created it, unless {@link
 * #withMode} gives it its own.
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
   * Muster. This chain is not changed.
   *
   * @param mode the mode
   * @return the chain with the given mode
   */
  public RuleChain<I> withMode(CheckMode mode) {
    return new RuleChain<>(inputType, steps, caller, locale, requireNonNull(mode, "mode"));
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
   * @throws IllegalArgumentException if {@code input} is not of the type the chain checks, which
   *     only unchecked code can pass
   * @throws IllegalStateException if a rule reports a violation under another rule's id
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
    var found = violations(input, mode);
    if (!found.isEmpty()) {
      throw new BusinessRuleViolationException(found);
    }
  }

  /**
   * Returns the violations of an input of the chain's type, in the order they are found, or an
   * empty list if it passes.
   */
  List<Violation> violations(Object input, CheckMode mode) {
    var callCaller = caller.get();
    var callLocale = requireNonNull(locale.get(), "the locale supplier returned null");
    // Created on the first failure, so that a passing check allocates no list.
    List<Violation> found = List.of();
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
      var violations = violationsOf(rule, input, ctx);
      if (violations == null) {
        passed[i] = true;
      } else if (mode == CheckMode.FAIL_FAST) {
        // Nothing was found before; the exception keeps the rule's unmodifiable list as it is.
        return violations;
      } else {
        if (found.isEmpty()) {
          found = new ArrayList<>();
        }
        found.addAll(violations);
      }
    }
    return found;
  }

  /** Runs a rule and returns its violations, or {@code null} if it passed. */
  private static @Nullable List<Violation> violationsOf(
      PreparedRule rule, Object input, RuleContext ctx) {
    var result = rule.instance().check(input, ctx);
    if (result == null) {
      throw new NullPointerException("rule '" + rule.id() + "' returned null");
    }
    if (!(result instanceof RuleResult.Failed failed)) {
      return null;
    }
    for (var violation : failed.violations()) {
      if (!violation.ruleId().equals(rule.id())) {
        throw new IllegalStateException(
            "rule '"
                + rule.id()
                + "' reported a violation of rule '"
                + violation.ruleId()
                + "'; build violations from the RuleContext the rule receives");
      }
    }
    return failed.violations();
  }

  /**
   * Describes this chain for developers and logs: the type it checks, its mode and its rules in the
   * order they run.
   *
   * @return the description
   */
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
