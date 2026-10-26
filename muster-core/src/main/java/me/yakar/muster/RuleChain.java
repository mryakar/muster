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
import me.yakar.muster.spi.CheckListener;
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
 * gives it its own, and tells the {@link CheckListener listeners} of that {@code Muster} about
 * every check. Chains are immutable and thread-safe.
 *
 * @param <I> the type of input
 */
public final class RuleChain<I> {

  private static final System.Logger LOGGER = System.getLogger("me.yakar.muster");

  /** What a listener is told a check is of: the name of the binding, or of the input type. */
  private final String name;

  private final Class<?> inputType;
  private final List<Step> steps;
  private final Supplier<? extends @Nullable Principal> caller;
  private final Supplier<Locale> locale;
  private final CheckMode mode;
  private final @Nullable CheckListener listener;

  RuleChain(
      String name,
      Class<?> inputType,
      List<Step> steps,
      Supplier<? extends @Nullable Principal> caller,
      Supplier<Locale> locale,
      CheckMode mode,
      @Nullable CheckListener listener) {
    this.name = name;
    this.inputType = inputType;
    this.steps = List.copyOf(steps);
    this.caller = caller;
    this.locale = locale;
    this.mode = mode;
    this.listener = listener;
  }

  /**
   * Returns a chain with the same rules that checks in the given mode, whatever the mode of the
   * {@code Muster}. This chain is not changed.
   *
   * @param mode the mode
   * @return the chain with the given mode
   */
  public RuleChain<I> withMode(CheckMode mode) {
    return new RuleChain<>(
        name, inputType, steps, caller, locale, requireNonNull(mode, "mode"), listener);
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
   * @param inputs the inputs; rules see an unmodifiable copy, taken when the check starts
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
   * given type and, if asked, of one class: a chain bound to a class checks that class only. The
   * inputs are copied once, before they are checked, so that a change to the collection during the
   * check reaches no rule, and every rule reads an element in constant time.
   */
  static List<?> elements(
      Collection<?> inputs, Class<?> inputType, boolean oneClass, String checker) {
    var copy = requireNonNull(inputs, "inputs").toArray();
    for (var position = 0; position < copy.length; position++) {
      var input = copy[position];
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
      if (oneClass && input.getClass() != copy[0].getClass()) {
        throw new IllegalArgumentException(
            "the elements of a checked collection must be of one class; the element at [0] is a "
                + copy[0].getClass().getTypeName()
                + ", the element at ["
                + position
                + "] a "
                + input.getClass().getTypeName());
      }
    }
    return Collections.unmodifiableList(Arrays.asList(copy));
  }

  /**
   * Checks the elements of a collection, which the caller knows to be of the chain's type, in the
   * given mode, and tells the listener about it. Kept apart from {@link #run}, so that the loop of
   * a single check stays as it is.
   */
  void runAll(List<?> inputs, CheckMode mode) {
    // An empty collection runs no rule, and a listener is not told of it: a collection checked by
    // the class of its elements has no chain without an element.
    if (inputs.isEmpty()) {
      return;
    }
    var observed =
        listener == null
            ? null
            : ObservedCheck.start(listener, name, inputType, mode, true, inputs.size());
    try {
      runEach(inputs, mode, observed);
    } catch (Throwable e) {
      if (observed != null) {
        observed.ended(e);
      }
      throw e;
    }
    if (observed != null) {
      observed.passed();
    }
  }

  /** Checks the elements of a collection, which is not empty. */
  private void runEach(List<?> inputs, CheckMode mode, @Nullable ObservedCheck observed) {
    var size = inputs.size();
    var callCaller = caller.get();
    var callLocale = requireNonNull(locale.get(), "the locale supplier returned null");
    ArrayList<Violation> found = null;
    // Whether each element passed each rule; a rule skipped for an element did not pass it.
    var passed = new boolean[steps.size()][];
    for (var i = 0; i < steps.size(); i++) {
      var step = steps.get(i);
      var rule = step.rule();
      if (observed != null) {
        observed.ruleStarted(rule);
      }
      passed[i] = new boolean[size];
      var eligible = eligible(step.requires(), passed, size);
      // A rule of the whole collection checks it only once every element passed what it requires.
      if (step.whole() ? eligible.length < size : eligible.length == 0) {
        if (observed != null) {
          observed.ruleSkipped();
        }
        continue;
      }
      var context = rule.context().toBuilder().locale(callLocale);
      if (callCaller != null) {
        context.caller(callCaller);
      }
      var ctx = context.build();
      if (!rule.instance().appliesTo(ctx)) {
        if (observed != null) {
          observed.ruleSkipped();
        }
        continue;
      }
      if (step.whole()) {
        var violations = violations(rule, rule.instance().check(inputs, ctx), "");
        if (violations == null) {
          Arrays.fill(passed[i], true);
          if (observed != null) {
            observed.rulePassed();
          }
          continue;
        }
        if (observed != null) {
          observed.ruleFailed(violations);
        }
        if (mode == CheckMode.FAIL_FAST) {
          throw new BusinessRuleViolationException(violations);
        }
        found = add(found, violations);
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
        // The violations the rule finds in collect-all mode follow this many in the list.
        var before = found == null ? 0 : found.size();
        for (var position : eligible) {
          var violations =
              violations(
                  rule,
                  rule.instance().check(inputs.get(position), ctx.element(position)),
                  " for the element at [" + position + "]");
          if (violations == null) {
            passed[i][position] = true;
          } else if (mode == CheckMode.FAIL_FAST) {
            if (observed != null) {
              observed.ruleFailed(violations);
            }
            throw new BusinessRuleViolationException(violations);
          } else {
            found = add(found, violations);
          }
        }
        if (observed != null) {
          endedOneByOne(observed, found, before);
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
        if (observed != null) {
          observed.rulePassed();
        }
        continue;
      }
      if (observed != null) {
        observed.ruleFailed(violations);
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
   * Tells the observer how a rule that checked the elements one by one, in collect-all mode, ended:
   * with the violations found since it started, or none.
   */
  private static void endedOneByOne(
      ObservedCheck observed, @Nullable ArrayList<Violation> found, int before) {
    if (found == null || found.size() == before) {
      observed.rulePassed();
    } else {
      observed.ruleFailed(List.copyOf(found.subList(before, found.size())));
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

  /**
   * Checks an input that the caller knows to be of the chain's type, in the given mode, and tells
   * the listener about it. One loop, so that the JIT compiles the check as a whole; without a
   * listener it pays one null check, and the loop tests a local that is null.
   */
  void run(Object input, CheckMode mode) {
    var observed =
        listener == null ? null : ObservedCheck.start(listener, name, inputType, mode, false, 1);
    try {
      var callCaller = caller.get();
      var callLocale = requireNonNull(locale.get(), "the locale supplier returned null");
      // Created on the first failure, so that a passing check allocates no list.
      ArrayList<Violation> found = null;
      var passed = new boolean[steps.size()];
      for (var i = 0; i < steps.size(); i++) {
        var step = steps.get(i);
        var rule = step.rule();
        if (observed != null) {
          observed.ruleStarted(rule);
        }
        if (!allPassed(step.requires(), passed)) {
          if (observed != null) {
            observed.ruleSkipped();
          }
          continue;
        }
        // From the context prepared with the rule, so that the rule id is not checked again.
        var context = rule.context().toBuilder().locale(callLocale);
        if (callCaller != null) {
          context.caller(callCaller);
        }
        var ctx = context.build();
        if (!rule.instance().appliesTo(ctx)) {
          if (observed != null) {
            observed.ruleSkipped();
          }
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
          if (observed != null) {
            observed.ruleFailed(violations);
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
          if (observed != null) {
            observed.rulePassed();
          }
        }
      }
      if (found != null) {
        throw new BusinessRuleViolationException(found);
      }
    } catch (Throwable e) {
      if (observed != null) {
        observed.ended(e);
      }
      throw e;
    }
    if (observed != null) {
      observed.passed();
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

  /**
   * Returns the ids of the rules of this chain, in the order they run: the listed rules, the rules
   * bound by type for the chain of a binding, and the rules they require, less the disabled ones.
   *
   * @return the ids, unmodifiable
   */
  public List<String> ruleIds() {
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
