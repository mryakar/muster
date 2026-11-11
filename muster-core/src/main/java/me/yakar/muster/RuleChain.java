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
import me.yakar.muster.internal.rule.Contexts;
import me.yakar.muster.internal.rule.PreparedRule;
import me.yakar.muster.internal.rule.Step;
import me.yakar.muster.internal.rule.ValueReads;
import me.yakar.muster.rule.RuleContext;
import me.yakar.muster.rule.RuleResult;
import me.yakar.muster.rule.RuleTarget;
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
 * <p>A chain checks in the mode of the {@code Muster} that created it, unless {@link #withMode}
 * gives it its own, and tells the {@link CheckListener listeners} of that {@code Muster} about
 * every check. A check that passes returns the values its {@linkplain
 * me.yakar.muster.rule.ValueRule value rules} left, as a {@link Checked}. Chains are immutable and
 * thread-safe.
 *
 * @param <I> the type of input
 */
public final class RuleChain<I> {

  private static final System.Logger LOGGER = System.getLogger("me.yakar.muster");

  /** The position of no element: the result is of the whole collection, or of a single input. */
  private static final int NO_ELEMENT = -1;

  /** No rule: what a rule that requires none requires. */
  private static final int[] NO_RULES = new int[0];

  /** What a listener is told a check is of: the name of the binding, or of the input type. */
  private final String name;

  private final Class<?> inputType;
  private final List<Step> steps;

  /**
   * Whether a rule of the chain requires the rule at each position, so that a check of a collection
   * records which elements passed it.
   */
  private final boolean[] required;

  /** Whether a rule of the chain leaves a value, so that a check keeps the values. */
  private final boolean valued;

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
    this.required = new boolean[this.steps.size()];
    var anyValue = false;
    for (var step : this.steps) {
      for (var index : step.requires()) {
        this.required[index] = true;
      }
      anyValue |= step.rule().valueType() != null;
    }
    this.valued = anyValue;
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
   * @return the check, with the values its value rules left
   * @throws BusinessRuleViolationException if a rule is violated
   * @throws IllegalArgumentException if {@code input} is not of the chain's type, which only
   *     unchecked code can pass
   */
  public Checked check(I input) {
    requireInstance(input, inputType, "chain");
    return run(input, mode);
  }

  /**
   * Checks the elements of a collection against the rules that apply to the call, in the mode of
   * this chain. Each rule checks the elements that passed the rules it requires. Any violation
   * rejects the whole call, and the target of a violation about an element starts with its
   * position, as in {@code [3].name}. Violations come rule by rule, in the order of the rules, and
   * within a rule as it reports them; a rule that does not override {@code checkAll} reports them
   * in the order of the elements. In {@link CheckMode#FAIL_FAST} mode, the check stops at the first
   * violated rule, and a rule that does not override {@code checkAll} at its first violated
   * element. An empty collection passes without running a rule.
   *
   * @param inputs the collection; rules see an unmodifiable copy, taken when the check starts
   * @return the check, with the values its value rules left
   * @throws BusinessRuleViolationException if a rule is violated
   * @throws NullPointerException if an element is {@code null}
   * @throws IllegalArgumentException if an element is not of the chain's type, which only unchecked
   *     code can pass
   */
  public Checked checkAll(Collection<? extends I> inputs) {
    return runAll(elements(inputs, inputType, false, "chain"), mode);
  }

  /** Checks that an input is of the type that a chain or a binding checks. */
  static void requireInstance(Object input, Class<?> inputType, String checker) {
    requireNonNull(input, "input");
    if (!inputType.isInstance(input)) {
      throw new IllegalArgumentException(
          "the "
              + checker
              + " checks "
              + inputType.getTypeName()
              + ", not "
              + input.getClass().getTypeName());
    }
  }

  /**
   * Returns the inputs of a check of a collection, after checking that every element is of the
   * given type and, for a check that binds rules to the class of its elements, that they are rule
   * targets of one class, or that none is a rule target. The inputs are copied once, before they
   * are checked, so that a change to the collection during the check reaches no rule, and every
   * rule reads an element in constant time.
   */
  static List<?> elements(
      Collection<?> inputs, Class<?> inputType, boolean byClass, String checker) {
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
      // Rules bind by type to rule targets only, so other elements may be of different classes.
      if (byClass
          && input.getClass() != copy[0].getClass()
          && (input instanceof RuleTarget || copy[0] instanceof RuleTarget)) {
        throw new IllegalArgumentException(
            "the elements of a checked collection must be rule targets of one class, or none a"
                + " rule target; the element at [0] is a "
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
   * Returns the class whose chain checks an input, or the elements of a collection, that bind rules
   * by type: the class of a rule target, or else the declared type, since rules bind by type to
   * rule targets only.
   */
  static Class<?> chainKey(Object input, Class<?> inputType) {
    return input instanceof RuleTarget ? input.getClass() : inputType;
  }

  /**
   * Checks the elements of a collection, which the caller knows to be of the chain's type, in the
   * given mode, and tells the listener about it. Kept apart from {@link #run}, so that the loop of
   * a single check stays as it is.
   *
   * @return the check, with the values its value rules left
   */
  Checked runAll(List<?> inputs, CheckMode mode) {
    // An empty collection runs no rule, and a listener is not told of it: a collection checked by
    // the class of its elements has no chain without an element.
    if (inputs.isEmpty()) {
      return Checked.EMPTY;
    }
    var observed = observe(mode, true, inputs.size());
    // The value each rule left: the value of each element, by position, for a rule of each element.
    var values = valued ? new @Nullable Object[steps.size()] : null;
    try {
      runEach(inputs, mode, observed, values);
    } catch (Throwable e) {
      if (observed != null) {
        observed.ended(e);
      }
      throw e;
    }
    if (observed != null) {
      observed.passed();
    }
    return values == null ? Checked.NONE : Checked.ofCollection(steps, values, inputs.size());
  }

  /** Checks the elements of a collection, which is not empty, and keeps the values in the array. */
  private void runEach(
      List<?> inputs,
      CheckMode mode,
      @Nullable ObservedCheck observed,
      @Nullable Object @Nullable [] values) {
    var size = inputs.size();
    var callCaller = caller.get();
    var callLocale = requireNonNull(locale.get(), "the locale supplier returned null");
    ArrayList<Violation> found = null;
    // Whether each element passed each rule that a rule requires; a rule skipped for an element did
    // not pass it. The rules that none requires, as the last rule, record it in one array that
    // nothing reads.
    var passed = new boolean[steps.size()][];
    var unread = new boolean[size];
    // The positions of every element, for the rules that require none, as the first rule. The rules
    // share it: the context of a rule that checks at once copies it, and the rest only read it.
    var all = eligible(NO_RULES, passed, size);
    for (var i = 0; i < steps.size(); i++) {
      var step = steps.get(i);
      var rule = step.rule();
      if (observed != null) {
        observed.ruleStarted(rule);
      }
      var marks = unread;
      // A value rule reads its own marks, which another rule must not have left.
      if (required[i] || rule.valueType() != null) {
        marks = new boolean[size];
        passed[i] = marks;
      }
      var eligible = step.requires().length == 0 ? all : eligible(step.requires(), passed, size);
      // A rule of the whole collection checks it only once every element passed what it requires.
      if (step.whole() ? eligible.length < size : eligible.length == 0) {
        if (observed != null) {
          observed.ruleSkipped();
        }
        continue;
      }
      var ctx = context(rule, callLocale, callCaller, step.collectionReads(), values);
      if (!rule.instance().appliesTo(ctx)) {
        if (observed != null) {
          observed.ruleSkipped();
        }
        continue;
      }
      var violations =
          step.whole()
              ? checkWhole(rule, inputs, ctx, marks, values, i)
              : rule.checksAll()
                  ? checkAtOnce(rule, inputs, ctx, eligible, marks, values, i)
                  : checkOneByOne(rule, inputs, ctx, eligible, marks, mode, values, i);
      if (violations == null) {
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
    }
    if (found != null) {
      throw new BusinessRuleViolationException(found);
    }
  }

  /**
   * Checks a collection with a rule of the whole collection, and marks every element as passed if
   * it passed.
   *
   * @return the violations, or {@code null} if the rule passed
   */
  private static @Nullable List<Violation> checkWhole(
      PreparedRule rule,
      List<?> inputs,
      RuleContext ctx,
      boolean[] passed,
      @Nullable Object @Nullable [] values,
      int slot) {
    var result = rule.instance().check(inputs, ctx);
    var violations = violations(rule, result, NO_ELEMENT);
    if (violations == null) {
      keep(values, slot, value(rule, result, NO_ELEMENT));
      Arrays.fill(passed, true);
    }
    return violations;
  }

  /**
   * Checks the eligible elements with a rule that checks them at once, and marks those it passed.
   *
   * @return the violations, or {@code null} if the rule passed
   */
  private static @Nullable List<Violation> checkAtOnce(
      PreparedRule rule,
      List<?> inputs,
      RuleContext ctx,
      int[] eligible,
      boolean[] passed,
      @Nullable Object @Nullable [] values,
      int slot) {
    // The positions also tell the context how many inputs there are, so that it rejects an index
    // past them.
    var result =
        rule.instance()
            .checkAll(
                eligible.length == inputs.size()
                    ? inputs
                    : Arrays.stream(eligible).mapToObj(inputs::get).toList(),
                ctx.toBuilder().positions(eligible).build());
    if (result instanceof RuleResult.Elements elements) {
      return checkElements(rule, elements.results(), eligible, passed, values, slot, inputs.size());
    }
    var violations = violations(rule, result, NO_ELEMENT);
    if (violations == null) {
      if (rule.valueType() == null) {
        // Checks that a rule that leaves no value passed without one.
        value(rule, result, NO_ELEMENT);
      }
      for (var position : eligible) {
        passed[position] = true;
      }
    } else {
      markPassed(passed, eligible, violations);
    }
    // A value rule leaves the value of each element it passes in the result of that element.
    if (rule.valueType() != null) {
      for (var position : eligible) {
        if (passed[position]) {
          throw new IllegalStateException(
              "value rule '"
                  + rule.id()
                  + "' checked a collection at once without the value of each element it passed;"
                  + " return the result of each element with RuleResult.ofElements");
        }
      }
    }
    return violations;
  }

  /**
   * Takes the result of each element from a rule that checked them at once: marks those it passed,
   * keeps their values, and gathers the violations of the others, which must be about them.
   *
   * @return the violations, in the order of the elements, or {@code null} if the rule passed
   */
  private static @Nullable List<Violation> checkElements(
      PreparedRule rule,
      List<RuleResult> results,
      int[] eligible,
      boolean[] passed,
      @Nullable Object @Nullable [] values,
      int slot,
      int size) {
    if (results.size() != eligible.length) {
      throw new IllegalStateException(
          "rule '"
              + rule.id()
              + "' returned "
              + results.size()
              + " results for "
              + eligible.length
              + " inputs; return the result of each input it received");
    }
    var each = rule.valueType() == null ? null : new @Nullable Object[size];
    ArrayList<Violation> found = null;
    for (var index = 0; index < eligible.length; index++) {
      var position = eligible[index];
      var result = results.get(index);
      var violations = violations(rule, result, position);
      if (violations == null) {
        var value = value(rule, result, position);
        if (each != null) {
          each[position] = value;
        }
        passed[position] = true;
        continue;
      }
      for (var violation : violations) {
        if (position(violation) != position) {
          throw new IllegalStateException(
              "rule '"
                  + rule.id()
                  + "' gave the input at index "
                  + index
                  + " a violation that is not about it; build it from ctx.element("
                  + index
                  + ")");
        }
      }
      found = add(found, violations);
    }
    keep(values, slot, each);
    return found;
  }

  /**
   * Checks the eligible elements one by one with a rule that does not override {@code checkAll},
   * and marks those it passed. In fail-fast mode it stops at the first violated element.
   *
   * @return the violations, in the order of the elements, or {@code null} if the rule passed
   */
  private static @Nullable List<Violation> checkOneByOne(
      PreparedRule rule,
      List<?> inputs,
      RuleContext ctx,
      int[] eligible,
      boolean[] passed,
      CheckMode mode,
      @Nullable Object @Nullable [] values,
      int slot) {
    if (rule.firstOneByOne()) {
      LOGGER.log(
          System.Logger.Level.WARNING,
          "Rule '"
              + rule.id()
              + "' checks the elements of a collection one by one, since it does not"
              + " override checkAll; override it to check them at once, or call"
              + " BusinessRule.super.checkAll from it to keep checking them one by one");
    }
    ArrayList<Violation> found = null;
    var each = rule.valueType() == null ? null : new @Nullable Object[inputs.size()];
    keep(values, slot, each);
    for (var position : eligible) {
      var result = rule.instance().check(inputs.get(position), ctx.element(position));
      var violations = violations(rule, result, position);
      if (violations == null) {
        var value = value(rule, result, position);
        if (each != null) {
          each[position] = value;
        }
        passed[position] = true;
      } else if (mode == CheckMode.FAIL_FAST) {
        return violations;
      } else {
        found = add(found, violations);
      }
    }
    return found;
  }

  /**
   * Returns the violations of a result, or {@code null} if it passed, after checking that they are
   * the rule's own. The position tells which element a null result was returned for, if any. Small
   * enough to be inlined; what a failure needs is in the methods it calls.
   */
  private static @Nullable List<Violation> violations(
      PreparedRule rule, RuleResult result, int position) {
    if (result instanceof RuleResult.Failed failed) {
      return requireOwn(rule, failed.violations());
    }
    if (result == null) {
      throw nullResult(rule, position);
    }
    return null;
  }

  /**
   * Returns the value of a result that is not failed, or {@code null} for a rule that leaves none,
   * after checking that it fits the rule: a value rule passes with a value of its value type, any
   * other without one. The position tells which element the result is of, if any. Small enough to
   * be inlined; what a failure needs is in the methods it calls.
   */
  private static @Nullable Object value(PreparedRule rule, RuleResult result, int position) {
    if (result instanceof RuleResult.Passed passed) {
      var value = passed.value();
      var valueType = rule.valueType();
      if (valueType == null ? value == null : valueType.isInstance(value)) {
        return value;
      }
      throw wrongValue(rule, value, position);
    }
    throw resultOfCollection(rule, position);
  }

  /** The exception for a result whose value does not fit the rule. */
  private static IllegalStateException wrongValue(
      PreparedRule rule, @Nullable Object value, int position) {
    var element = position == NO_ELEMENT ? "" : " for the element at [" + position + "]";
    var valueType = rule.valueType();
    if (valueType == null) {
      return new IllegalStateException(
          "rule '"
              + rule.id()
              + "' passed with a value"
              + element
              + ", but is not a value rule; implement ValueRule<Input, Value> to leave a value");
    }
    return new IllegalStateException(
        value == null
            ? "value rule '"
                + rule.id()
                + "' passed without a value"
                + element
                + "; pass with RuleResult.passed(value)"
            : "value rule '"
                + rule.id()
                + "' left a "
                + value.getClass().getTypeName()
                + element
                + ", not a "
                + valueType.getTypeName());
  }

  /** The exception for the result of a collection where the result of one input belongs. */
  private static IllegalStateException resultOfCollection(PreparedRule rule, int position) {
    return new IllegalStateException(
        "rule '"
            + rule.id()
            + "' returned the result of a collection"
            + (position == NO_ELEMENT ? "" : " for the element at [" + position + "]")
            + "; RuleResult.ofElements is for checkAll");
  }

  /** Keeps the value a rule left in its slot, if the check keeps values. */
  private static void keep(@Nullable Object @Nullable [] values, int slot, @Nullable Object value) {
    if (values != null) {
      values[slot] = value;
    }
  }

  /** Returns the violations a rule reported, after checking that they are its own. */
  private static List<Violation> requireOwn(PreparedRule rule, List<Violation> violations) {
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
    return violations;
  }

  /**
   * The exception for a rule that returned null, for an element or not. The message is built only
   * then, so that a check of each element allocates nothing for it.
   */
  private static NullPointerException nullResult(PreparedRule rule, int position) {
    return new NullPointerException(
        "rule '"
            + rule.id()
            + "' returned null"
            + (position == NO_ELEMENT ? "" : " for the element at [" + position + "]"));
  }

  /**
   * Returns the context of a rule for one call: the one prepared with the rule, so that the rule id
   * is not checked again, with the locale and the caller of the call.
   */
  private static RuleContext context(
      PreparedRule rule,
      Locale locale,
      @Nullable Principal caller,
      @Nullable ValueReads reads,
      @Nullable Object @Nullable [] values) {
    if (reads != null) {
      return Contexts.create(rule.context(), locale, caller, reads, requireNonNull(values));
    }
    var context = rule.context().toBuilder().locale(locale);
    if (caller != null) {
      context.caller(caller);
    }
    return context.build();
  }

  /** Tells the listener, if there is one, that a check starts. */
  private @Nullable ObservedCheck observe(CheckMode mode, boolean collection, int size) {
    return listener == null
        ? null
        : ObservedCheck.start(listener, name, inputType, mode, collection, size);
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
   * the listener about it; without a listener it pays one null check. Small, so that the JIT
   * inlines it into its caller, which then catches the exception it throws without unwinding a
   * frame.
   *
   * @return the check, with the values its value rules left
   */
  Checked run(Object input, CheckMode mode) {
    var observed = observe(mode, false, 1);
    // The value each rule left, kept only by a chain that has a value rule.
    var values = valued ? new @Nullable Object[steps.size()] : null;
    try {
      var found = checkRules(input, mode, observed, values);
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
    return values == null ? Checked.NONE : Checked.ofInput(steps, values);
  }

  /**
   * Runs the rules of a single check, one loop, so that the JIT compiles the loop as a whole, and
   * returns the violations they found, or {@code null}; it keeps the values in the array. The loop
   * tests a local observer that is null without a listener. It stays one method that is too large
   * to be inlined, and so checks results and builds contexts itself rather than through the helpers
   * of the collection check: the loop runs faster per rule compiled on its own than inlined into
   * the callers of {@link #run}.
   */
  private @Nullable List<Violation> checkRules(
      Object input,
      CheckMode mode,
      @Nullable ObservedCheck observed,
      @Nullable Object @Nullable [] values) {
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
      RuleContext ctx;
      var reads = step.reads();
      if (reads == null) {
        // From the context prepared with the rule, so that the rule id is not checked again.
        var context = rule.context().toBuilder().locale(callLocale);
        if (callCaller != null) {
          context.caller(callCaller);
        }
        ctx = context.build();
      } else {
        ctx =
            Contexts.create(rule.context(), callLocale, callCaller, reads, requireNonNull(values));
      }
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
          return violations;
        }
        if (found == null) {
          found = new ArrayList<>();
        }
        found.addAll(violations);
      } else {
        var value = value(rule, result, NO_ELEMENT);
        if (value != null) {
          requireNonNull(values)[i] = value;
        }
        passed[i] = true;
        if (observed != null) {
          observed.rulePassed();
        }
      }
    }
    return found;
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
   * Returns the ids of the rules of this chain, in the order they run: the listed rules and the
   * rules they require, less the disabled ones.
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
