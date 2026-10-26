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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
 * The rules that check inputs of one type, prepared once for repeated use. Get one from {@link
 * Muster#chain} and keep it.
 *
 * <p>Rules run in the order they are listed. Before each one, the rules it {@linkplain
 * me.yakar.muster.rule.RequiresRule requires} that have not run yet run first, each after its own
 * requirements: listed ones in list order, the others in the order of their ids. The order is the
 * same on every call, and in {@link CheckMode#FAIL_FAST} mode, which stops at the first violated
 * rule, it decides which violation is reported, so list cheap rules first.
 *
 * <p>{@link #checkAll} checks the elements of a collection in one call: each rule checks all of
 * them, at once if it overrides {@link me.yakar.muster.rule.BusinessRule#checkAll}, and a rule that
 * requires another checks the elements that passed it.
 *
 * <p>A chain checks in the mode of the {@code Muster} that created it, unless {@link #withMode}
 * gives it its own. Chains are immutable and thread-safe.
 *
 * @param <I> the type of input
 */
public final class RuleChain<I> {

  private static final System.Logger LOGGER = System.getLogger("me.yakar.muster");

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

  /**
   * Checks the elements of a collection against the rules that apply to the call, in the mode of
   * this chain. Each rule checks the elements that passed the rules it requires; one violated
   * element rejects the whole call. Violations come rule by rule, in the order of the rules, and
   * within a rule as it reports them; a rule that does not override {@code checkAll} reports them
   * in the order of the elements. In {@link CheckMode#FAIL_FAST} mode, the check stops at the first
   * violated rule, and a rule that does not override {@code checkAll} at its first violated
   * element. An empty collection passes without running a rule.
   *
   * @param inputs the inputs; a list is passed on unmodifiable, and another collection as a copy
   * @throws BusinessRuleViolationException if a rule is violated
   * @throws NullPointerException if an element is {@code null}
   * @throws IllegalArgumentException if an element is not of the chain's type, which only unchecked
   *     code can pass
   */
  public void checkAll(Collection<? extends I> inputs) {
    runAll(elements(inputs, inputType, false, "chain"), mode);
  }

  /**
   * Returns the inputs of a check of a collection, after checking that every element is of the
   * given type and, if asked, of one class: a chain bound to a class checks that class only.
   */
  static List<?> elements(
      Collection<?> inputs, Class<?> inputType, boolean oneClass, String checker) {
    requireNonNull(inputs, "inputs");
    Class<?> first = null;
    var position = 0;
    for (var input : inputs) {
      if (input == null) {
        throw new NullPointerException("the element at [" + position + "] is null");
      }
      if (!inputType.isInstance(input)) {
        throw new IllegalArgumentException(
            "the "
                + checker
                + " checks "
                + inputType.getTypeName()
                + "; the element at ["
                + position
                + "] is a "
                + input.getClass().getTypeName());
      }
      if (first == null) {
        first = input.getClass();
      } else if (oneClass && input.getClass() != first) {
        throw new IllegalArgumentException(
            "the elements of a checked collection must be of one class; the element at [0] is a "
                + first.getTypeName()
                + ", the element at ["
                + position
                + "] a "
                + input.getClass().getTypeName());
      }
      position++;
    }
    return inputs instanceof List<?> list
        ? Collections.unmodifiableList(list)
        : List.copyOf(inputs);
  }

  /**
   * Checks the elements of a collection, which the caller knows to be of the chain's type, in the
   * given mode. Kept apart from {@link #run}, so that the loop of a single check stays as it is.
   */
  void runAll(List<?> inputs, CheckMode mode) {
    var size = inputs.size();
    if (size == 0) {
      return;
    }
    var callCaller = caller.get();
    var callLocale = requireNonNull(locale.get(), "the locale supplier returned null");
    ArrayList<Violation> found = null;
    // Whether each element passed each rule; a rule skipped for an element did not pass it.
    var passed = new boolean[steps.size()][];
    for (var i = 0; i < steps.size(); i++) {
      var step = steps.get(i);
      passed[i] = new boolean[size];
      var eligible = eligible(step.requires(), passed, size);
      if (eligible.length == 0) {
        continue;
      }
      var rule = step.rule();
      var context = rule.context().toBuilder().locale(callLocale);
      if (callCaller != null) {
        context.caller(callCaller);
      }
      var ctx = context.build();
      if (!rule.instance().appliesTo(ctx)) {
        continue;
      }
      if (!rule.checksAll()) {
        if (rule.firstOneByOne()) {
          LOGGER.log(
              System.Logger.Level.WARNING,
              "Rule '"
                  + rule.id()
                  + "' checks the elements of a collection one by one, since it does not"
                  + " override checkAll; override it to check them at once, or call"
                  + " BusinessRule.super.checkAll from it to keep checking them one by one");
        }
        for (var position : eligible) {
          var violations =
              violations(
                  rule,
                  rule.instance().check(inputs.get(position), ctx.element(position)),
                  " for the element at [" + position + "]");
          if (violations == null) {
            passed[i][position] = true;
          } else if (mode == CheckMode.FAIL_FAST) {
            throw new BusinessRuleViolationException(violations);
          } else {
            found = add(found, violations);
          }
        }
        continue;
      }
      // The positions also tell the context how many inputs there are, so that it rejects an index
      // past them.
      var violations =
          violations(
              rule,
              rule.instance()
                  .checkAll(
                      eligible.length == size
                          ? inputs
                          : Arrays.stream(eligible).mapToObj(inputs::get).toList(),
                      ctx.toBuilder().positions(eligible).build()),
              "");
      if (violations == null) {
        for (var position : eligible) {
          passed[i][position] = true;
        }
        continue;
      }
      if (mode == CheckMode.FAIL_FAST) {
        throw new BusinessRuleViolationException(violations);
      }
      markPassed(passed[i], eligible, violations);
      found = add(found, violations);
    }
    if (found != null) {
      throw new BusinessRuleViolationException(found);
    }
  }

  /**
   * Returns the violations of a result, or {@code null} if it passed, after checking that they are
   * the rule's own. The suffix tells what a null result was returned for.
   */
  private static @Nullable List<Violation> violations(
      PreparedRule rule, RuleResult result, String suffix) {
    if (result == null) {
      throw new NullPointerException("rule '" + rule.id() + "' returned null" + suffix);
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

  private static ArrayList<Violation> add(
      @Nullable ArrayList<Violation> found, List<Violation> violations) {
    var list = found == null ? new ArrayList<Violation>() : found;
    list.addAll(violations);
    return list;
  }

  /**
   * Marks the elements that passed a rule that checked them at once: those that no violation names
   * by its position, unless a violation names none of the checked elements, which makes it about
   * them all.
   */
  private static void markPassed(boolean[] passed, int[] eligible, List<Violation> violations) {
    var failed = new boolean[passed.length];
    for (var violation : violations) {
      var position = position(violation);
      if (position < 0 || Arrays.binarySearch(eligible, position) < 0) {
        return;
      }
      failed[position] = true;
    }
    for (var position : eligible) {
      passed[position] = !failed[position];
    }
  }

  /**
   * Returns the position of the element a violation is about, as {@link RuleContext#element} puts
   * it in front of the target, or -1 if the target names none.
   */
  static int position(Violation violation) {
    var target = violation.target().orElse("");
    if (!target.startsWith("[")) {
      return -1;
    }
    // ASCII digits only, as the context writes them; no sign, and no digits of other scripts.
    long position = 0;
    var end = 1;
    for (; end < target.length() && target.charAt(end) >= '0' && target.charAt(end) <= '9'; end++) {
      position = position * 10 + target.charAt(end) - '0';
      if (position > Integer.MAX_VALUE) {
        return -1;
      }
    }
    return end > 1 && end < target.length() && target.charAt(end) == ']' ? (int) position : -1;
  }

  /** Returns the positions of the elements that passed every rule a rule requires, in order. */
  private static int[] eligible(int[] required, boolean[][] passed, int size) {
    var eligible = new int[size];
    var count = 0;
    for (var position = 0; position < size; position++) {
      if (allPassed(required, passed, position)) {
        eligible[count++] = position;
      }
    }
    return count == size ? eligible : Arrays.copyOf(eligible, count);
  }

  private static boolean allPassed(int[] required, boolean[][] passed, int position) {
    for (var index : required) {
      if (!passed[index][position]) {
        return false;
      }
    }
    return true;
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
