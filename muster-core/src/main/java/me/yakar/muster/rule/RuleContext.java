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
package me.yakar.muster.rule;

import static java.util.Objects.requireNonNull;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import me.yakar.muster.internal.rule.Contexts;
import me.yakar.muster.internal.rule.RuleDefinition;
import me.yakar.muster.internal.rule.ValueReads;
import org.jspecify.annotations.Nullable;

/**
 * What a rule knows about the call it checks, besides its input: who makes the call, in which
 * locale, and the values that the rules it requires left.
 *
 * <p>Each rule receives its own context. Rules create their violations through it, so every
 * violation carries the id of the rule that reported it. Contexts are immutable and thread-safe.
 *
 * <p>A rule that checks a collection at once, in {@link BusinessRule#checkAll}, gets the context of
 * the collection; {@link #element(int)} gives the context of one of its elements: the target of its
 * violations starts with the position of the element.
 *
 * <p>A rule reads the values that the {@link ValueRule}s it requires left with {@link
 * #valueOf(Class)}.
 *
 * <p>The context holds what differs from call to call. What stays the same, such as a {@link
 * java.time.Clock} or a repository, is a dependency of the rule: take it in the constructor, and a
 * test can pass a fixed clock.
 */
public sealed class RuleContext {

  /** No element: the context is not about one element of a collection. */
  private static final int NONE = -1;

  static {
    // The engine creates the contexts that read values; RuleContext is initialized before any rule
    // is prepared, and Contexts reads nothing of this class while it is initialized.
    Contexts.register(
        (prepared, locale, caller, reads, values) ->
            new Extended(prepared.ruleId, caller, locale, null, NONE, reads, values));
  }

  private final String ruleId;
  private final @Nullable Principal caller;
  private final Locale locale;

  private RuleContext(String ruleId, @Nullable Principal caller, Locale locale) {
    this.ruleId = ruleId;
    this.caller = caller;
    this.locale = locale;
  }

  /**
   * Creates a context for the rule with the given id, with no caller and the root locale. Use it to
   * test a rule on its own.
   *
   * @param ruleId the id of the rule being checked
   * @return the context
   * @throws IllegalArgumentException if {@code ruleId} is not a valid rule id
   */
  public static RuleContext forRule(String ruleId) {
    return builder(ruleId).build();
  }

  /**
   * Creates a context for the given rule class, with no caller and the root locale. Use it to test
   * a rule on its own without repeating its id.
   *
   * @param ruleClass the class of the rule being checked
   * @return the context
   * @throws IllegalArgumentException if the class has no {@code @Rule} or its id is invalid
   */
  public static RuleContext forRule(Class<? extends BusinessRule<?>> ruleClass) {
    return builder(ruleClass).build();
  }

  /**
   * Starts a context for the given rule class, reading the id from its {@code @Rule}. Unless set,
   * the context has no caller and the root locale.
   *
   * @param ruleClass the class of the rule being checked
   * @return a builder for the context
   * @throws IllegalArgumentException if the class has no {@code @Rule} or its id is invalid
   */
  public static Builder builder(Class<? extends BusinessRule<?>> ruleClass) {
    return builder(RuleDefinition.idOf(requireNonNull(ruleClass, "ruleClass")));
  }

  /**
   * Starts a context for the rule with the given id. Unless set, the context has no caller and the
   * root locale.
   *
   * @param ruleId the id of the rule being checked
   * @return a builder for the context
   * @throws IllegalArgumentException if {@code ruleId} is not a valid rule id
   */
  public static Builder builder(String ruleId) {
    return new Builder(ruleId);
  }

  /**
   * Returns a builder that starts from this context: the same rule, caller and locale, the same
   * positions of inputs or position of an element, and the same values to read. Use it to create
   * the context of the same rule for another call. A context that Muster passes to a rule reads the
   * values of its own check, so start the context of another call from one without them, such as
   * {@link #forRule(Class)}.
   *
   * @return the builder
   */
  public Builder toBuilder() {
    return new Builder(this);
  }

  /**
   * Returns the id of the rule being checked.
   *
   * @return the rule id
   */
  public String ruleId() {
    return ruleId;
  }

  /**
   * Returns who makes the call. Integrations pass their own principal type, such as Spring
   * Security's {@code Authentication}; cast to it when the rule needs more than the name.
   *
   * @return the caller, or empty for an anonymous or system call
   */
  public Optional<Principal> caller() {
    return Optional.ofNullable(caller);
  }

  /**
   * Returns the locale of the call, for business logic that depends on the caller's language or
   * region; messages are resolved elsewhere. Do not normalize unique data, such as names, in this
   * locale: the same name could pass in one language and fail in another, as Turkish dotted and
   * dotless i do. Normalize it in one fixed locale.
   *
   * @return the locale
   */
  public Locale locale() {
    return locale;
  }

  /**
   * Returns the context of one of the inputs that {@link BusinessRule#checkAll} receives. Its
   * violations are about that element: their target starts with the position of the element in the
   * checked collection, as in {@code [3].name}.
   *
   * <p>The index is that of the input in the list that {@code checkAll} receives. When Muster
   * leaves out elements that failed a rule this rule requires, the list is shorter than the
   * collection, and the position in the target is still the element's in the collection.
   *
   * @param index the index of the input in the list that {@code checkAll} receives
   * @return the context of that element
   * @throws IllegalArgumentException if {@code index} is negative or, in a context that knows the
   *     positions of the inputs, as every context that Muster passes to {@code checkAll} does, not
   *     the index of an input
   * @throws IllegalStateException if this is already the context of an element
   */
  public RuleContext element(int index) {
    var element = position();
    if (element != NONE) {
      throw new IllegalStateException(
          "this is the context of the element at ["
              + element
              + "] already; get the contexts of elements from the context that checkAll receives");
    }
    var positions = positions();
    int position;
    if (positions == null) {
      if (index < 0) {
        throw new IllegalArgumentException("the index " + index + " is negative");
      }
      position = index;
    } else {
      if (index < 0 || index >= positions.length) {
        throw new IllegalArgumentException(
            "the index " + index + " is not that of one of the " + positions.length + " inputs");
      }
      position = positions[index];
    }
    return new Extended(ruleId, caller, locale, null, position, reads(), values());
  }

  /**
   * Returns the position, in the checked collection, of the element this context is about.
   *
   * @return the position, or empty if this context is not about an element
   */
  public OptionalInt elementPosition() {
    var element = position();
    return element == NONE ? OptionalInt.empty() : OptionalInt.of(element);
  }

  /**
   * Returns the value that a {@link ValueRule} left, which this rule requires, directly or through
   * other rules. That rule passed, since this one runs only then, so the value is always there.
   *
   * {@snippet :
   * Vehicle vehicle = ctx.valueOf(VehicleExists.class);
   * }
   *
   * <p>In a check of a collection, a rule of each element leaves a value for each element: read it
   * from the context of the element, as a rule that checks at once does with {@code
   * ctx.element(i).valueOf(VehicleExists.class)}. In a check, a value that a rule of the whole
   * collection left is read from any context.
   *
   * <p>In a context that a test built, a value given with {@link Builder#value} is read from that
   * context only, and the value of each element given with {@link Builder#values} from the context
   * of an element only.
   *
   * @param rule the class of the value rule
   * @param <V> the type of value it leaves
   * @return the value
   * @throws IllegalStateException if this rule does not require that rule, or, in a check of a
   *     collection, reads the value of each element from a context that is not about an element; in
   *     a context that a test built, if the value was not given, or not for the context it is read
   *     from
   */
  @SuppressWarnings("unchecked") // Muster and the builder check the value against the rule's type.
  public <V> V valueOf(Class<? extends ValueRule<?, V>> rule) {
    var reads = reads();
    var index = reads == null ? -1 : reads.indexOf(rule);
    if (reads == null || index < 0) {
      throw noValue(rule, reads);
    }
    var value = requireNonNull(values())[reads.slot(index)];
    var element = position();
    if (reads.perElement(index)) {
      if (element == NONE) {
        throw new IllegalStateException(
            "rule '"
                + RuleDefinition.idOf(rule)
                + "' leaves a value for each element; read it from the context of an element, as"
                + " in ctx.element(i).valueOf("
                + rule.getSimpleName()
                + ".class)");
      }
      var each = (@Nullable Object[]) requireNonNull(value);
      var found = element < each.length ? each[element] : null;
      if (found == null) {
        throw new IllegalStateException(
            "no value of rule '"
                + RuleDefinition.idOf(rule)
                + "' was given for the element at ["
                + element
                + "]; give one for each input with RuleContext.Builder.values");
      }
      return (V) found;
    }
    if (element != NONE && reads.given()) {
      throw new IllegalStateException(
          "the value of rule '"
              + RuleDefinition.idOf(rule)
              + "' was given for the whole context, not for each element; give one for each"
              + " input with RuleContext.Builder.values");
    }
    return (V) requireNonNull(value);
  }

  /**
   * The exception for a value that this context cannot read: of a rule this rule does not require,
   * or that a test did not give. A context with no values at all may be either.
   */
  private IllegalStateException noValue(Class<?> rule, @Nullable ValueReads reads) {
    var id = RuleDefinition.idOf(rule);
    var require =
        "add " + rule.getSimpleName() + " to its @RequiresRule, or to that of a rule it requires";
    if (reads == null) {
      return new IllegalStateException(
          "rule '"
              + ruleId
              + "' has no value of rule '"
              + id
              + "': "
              + require
              + "; in a test, give the value with RuleContext.Builder.value");
    }
    return new IllegalStateException(
        reads.given()
            ? "no value of rule '"
                + id
                + "' was given to this context; give it with RuleContext.Builder.value, or with"
                + " values for each element"
            : "rule '"
                + ruleId
                + "' reads the value of rule '"
                + id
                + "', which it does not require; "
                + require);
  }

  /**
   * Starts a violation whose code is the rule id.
   *
   * @return a builder for the violation
   */
  public Violation.Builder violation() {
    // A constant position, so that escape analysis can remove the builder of a single check.
    return new Violation.Builder(ruleId, NONE);
  }

  /**
   * Starts a violation with the given code. Use it when a rule reports more than one kind of
   * problem.
   *
   * @param code the stable, machine-readable code of the violation
   * @return a builder for the violation
   * @throws IllegalArgumentException if {@code code} is blank or contains whitespace, control or
   *     format characters, or unpaired surrogates
   */
  public Violation.Builder violation(String code) {
    return new Violation.Builder(ruleId, code, NONE);
  }

  /**
   * Describes this context for developers and logs: the rule id, the caller's name if there is a
   * caller, the locale, and the position of the element if the context is about one.
   *
   * @return the description
   */
  @Override
  public String toString() {
    var text = new StringBuilder("RuleContext[ruleId=").append(ruleId);
    if (caller != null) {
      text.append(", caller=").append(caller.getName());
    }
    text.append(", locale=").append(locale.toLanguageTag());
    var element = position();
    if (element != NONE) {
      text.append(", element=").append(element);
    }
    return text.append(']').toString();
  }

  /**
   * Builds a {@link RuleContext}. Get one from {@link RuleContext#builder(Class)}, {@link
   * RuleContext#builder(String)}, or from {@link RuleContext#toBuilder()} to start from an existing
   * context. A builder is not safe for use by multiple threads.
   */
  public static final class Builder {

    private final String ruleId;
    private @Nullable Principal caller;
    private Locale locale = Locale.ROOT;
    private int @Nullable [] positions;
    private int element = NONE;
    private @Nullable ValueReads reads;
    private @Nullable Object @Nullable [] values;

    /**
     * The values a test gave, by rule class: a value, a list with the value of each input, or the
     * values of each element that a context built before placed at their positions. Created on the
     * first value, so that the builders of a check allocate no map.
     */
    private @Nullable Map<Class<?>, Object> given;

    private Builder(String ruleId) {
      this.ruleId = Checks.requireRuleId(ruleId);
    }

    private Builder(RuleContext context) {
      // The id was checked when the context was built.
      ruleId = context.ruleId;
      caller = context.caller;
      locale = context.locale;
      if (context instanceof Extended extended) {
        positions = extended.positions;
        element = extended.element;
        var readable = extended.reads;
        var those = extended.values;
        if (readable != null && readable.given()) {
          // Values that a test gave stay given, so that more can be given.
          var map = new LinkedHashMap<Class<?>, Object>();
          for (var i = 0; i < readable.size(); i++) {
            var value = requireNonNull(requireNonNull(those)[readable.slot(i)]);
            map.put(
                readable.rule(i),
                readable.perElement(i) ? new Placed((@Nullable Object[]) value) : value);
          }
          given = map;
        } else {
          reads = readable;
          values = those;
        }
      }
    }

    /**
     * Sets who makes the call.
     *
     * @param caller the caller
     * @return this builder
     */
    public Builder caller(Principal caller) {
      this.caller = requireNonNull(caller, "caller");
      return this;
    }

    /**
     * Sets the locale of the call.
     *
     * @param locale the locale
     * @return this builder
     */
    public Builder locale(Locale locale) {
      this.locale = requireNonNull(locale, "locale");
      return this;
    }

    /**
     * Sets the positions, in the checked collection, of the inputs that {@link
     * BusinessRule#checkAll} receives, by their index in its list: {@link RuleContext#element(int)
     * element(i)} is then about the element at {@code positions[i]}, and rejects an index past
     * them. Muster sets them on every call of {@code checkAll}; use it to test a rule on part of a
     * collection. The context built is not about an element.
     *
     * @param positions the positions, in ascending order
     * @return this builder
     * @throws IllegalArgumentException if a position is negative, or not greater than the one
     *     before
     */
    public Builder positions(int... positions) {
      var copy = requireNonNull(positions, "positions").clone();
      for (var i = 0; i < copy.length; i++) {
        if (copy[i] < 0 || (i > 0 && copy[i] <= copy[i - 1])) {
          throw new IllegalArgumentException(
              "the positions must be ascending and not negative: " + Arrays.toString(copy));
        }
      }
      this.positions = copy;
      element = NONE;
      return this;
    }

    /**
     * Gives the value that a {@link ValueRule} left, so that a test can check a rule that reads it
     * on its own. Once a value is given, the context reads only the given values. The context reads
     * it, but the context of an element does not: give the values of each element with {@link
     * #values}.
     *
     * @param rule the class of the value rule
     * @param value the value it left
     * @param <V> the type of value it leaves
     * @return this builder
     * @throws IllegalArgumentException if the class is not a value rule, or the value is not of its
     *     value type
     */
    public <V> Builder value(Class<? extends ValueRule<?, V>> rule, V value) {
      give(rule, requireValue(rule, value));
      return this;
    }

    /** Keeps a value a test gave, replacing any given before for the same rule. */
    private void give(Class<?> rule, Object value) {
      var map = given;
      if (map == null) {
        map = new LinkedHashMap<>();
        given = map;
      }
      map.put(rule, value);
    }

    /**
     * Gives the value that a {@link ValueRule} left for each input of a collection, so that a test
     * can check a rule that reads them on its own: {@code ctx.element(i).valueOf(rule)} reads the
     * {@code i}th. Once a value is given, the context reads only the given values. The values are
     * placed at the positions of the inputs that the builder has when it builds the context.
     *
     * @param rule the class of the value rule
     * @param values the value of each input, in the order of the inputs; as many as the {@linkplain
     *     #positions(int...) positions}, if set
     * @param <V> the type of value it leaves
     * @return this builder
     * @throws IllegalArgumentException if the class is not a value rule, or a value is not of its
     *     value type
     */
    public <V> Builder values(Class<? extends ValueRule<?, V>> rule, List<? extends V> values) {
      var copy = new ArrayList<Object>(requireNonNull(values, "values").size());
      for (var value : values) {
        copy.add(requireValue(rule, value));
      }
      give(rule, List.copyOf(copy));
      return this;
    }

    /** Checks that a given value is of the value type of the rule. */
    private static Object requireValue(Class<?> rule, @Nullable Object given) {
      var valueType = RuleDefinition.of(requireNonNull(rule, "rule")).valueType();
      if (valueType == null) {
        throw new IllegalArgumentException(
            rule.getTypeName() + " is not a value rule; it leaves no value");
      }
      var value = requireNonNull(given, "value");
      if (!valueType.isInstance(value)) {
        throw new IllegalArgumentException(
            "the value of "
                + rule.getTypeName()
                + " is a "
                + value.getClass().getTypeName()
                + ", not a "
                + valueType.getTypeName());
      }
      return value;
    }

    /**
     * Builds the context. The builder can be used again; later changes do not affect contexts
     * already built.
     *
     * @return the context
     * @throws IllegalArgumentException if the values of each input were given for a number of
     *     inputs other than that of the positions
     */
    public RuleContext build() {
      var builtReads = reads;
      var builtValues = values;
      var map = given;
      if (map != null) {
        var rules = List.copyOf(map.keySet());
        var perElement = new boolean[rules.size()];
        builtValues = new Object[rules.size()];
        for (var i = 0; i < rules.size(); i++) {
          var value = map.get(rules.get(i));
          if (value instanceof List<?> each) {
            perElement[i] = true;
            builtValues[i] = byPosition(rules.get(i), each);
          } else if (value instanceof Placed placed) {
            perElement[i] = true;
            builtValues[i] = placed.byPosition();
          } else {
            builtValues[i] = value;
          }
        }
        builtReads = ValueReads.given(rules, perElement);
      }
      return positions == null && element == NONE && builtReads == null
          ? new RuleContext(ruleId, caller, locale)
          : new Extended(ruleId, caller, locale, positions, element, builtReads, builtValues);
    }

    /** The values of each element that a context built before placed at their positions. */
    private record Placed(@Nullable Object[] byPosition) {}

    /** Places the values of each input at the position of its element in the collection. */
    private Object[] byPosition(Class<?> rule, List<?> each) {
      var at = positions;
      if (at == null) {
        return each.toArray();
      }
      if (each.size() != at.length) {
        throw new IllegalArgumentException(
            "the values of "
                + rule.getTypeName()
                + " are "
                + each.size()
                + ", the inputs "
                + at.length);
      }
      var byPosition = new Object[at.length == 0 ? 0 : at[at.length - 1] + 1];
      for (var i = 0; i < at.length; i++) {
        byPosition[at[i]] = each.get(i);
      }
      return byPosition;
    }
  }

  /** The positions of the inputs that {@code checkAll} receives, or {@code null}. */
  private int @Nullable [] positions() {
    return this instanceof Extended extended ? extended.positions : null;
  }

  /** The position of the element this context is about, or {@link #NONE}. */
  private int position() {
    return this instanceof Extended extended ? extended.element : NONE;
  }

  /** The values this context may read, or {@code null}. */
  private @Nullable ValueReads reads() {
    return this instanceof Extended extended ? extended.reads : null;
  }

  /** The values of the check, by slot, or {@code null}. */
  private @Nullable Object @Nullable [] values() {
    return this instanceof Extended extended ? extended.values : null;
  }

  /**
   * The context of a collection whose inputs have positions, of one of its elements, or of a rule
   * that reads values. Only these carry more, so that the context of a rule in a single check that
   * reads no value keeps three fields and stays as small as it can be: each rule of a check gets
   * its own.
   */
  private static final class Extended extends RuleContext {

    /**
     * The positions, in the checked collection, of the inputs that {@code checkAll} receives, by
     * their index in its list; {@code null} where they are the same.
     */
    private final int @Nullable [] positions;

    /** The position, in the checked collection, of the element this context is about, or none. */
    private final int element;

    /** The values this context may read, or {@code null}. */
    private final @Nullable ValueReads reads;

    /**
     * The values of the check, by slot, which the check fills as rules pass; the context reads only
     * the slots of rules that passed before it was created. {@code null} without reads.
     */
    private final @Nullable Object @Nullable [] values;

    private Extended(
        String ruleId,
        @Nullable Principal caller,
        Locale locale,
        int @Nullable [] positions,
        int element,
        @Nullable ValueReads reads,
        @Nullable Object @Nullable [] values) {
      super(ruleId, caller, locale);
      this.positions = positions;
      this.element = element;
      this.reads = reads;
      this.values = values;
    }

    @Override
    public Violation.Builder violation() {
      return new Violation.Builder(ruleId(), element);
    }

    @Override
    public Violation.Builder violation(String code) {
      return new Violation.Builder(ruleId(), code, element);
    }
  }
}
