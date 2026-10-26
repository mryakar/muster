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
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import me.yakar.muster.internal.rule.RuleDefinition;
import org.jspecify.annotations.Nullable;

/**
 * What a rule knows about the call it checks, besides its input: who makes the call and in which
 * locale.
 *
 * <p>Each rule receives its own context. Rules create their violations through it, so every
 * violation carries the id of the rule that reported it. Contexts are immutable and thread-safe.
 *
 * <p>A rule that checks a collection at once, in {@link BusinessRule#checkAll}, gets the context of
 * the collection; {@link #element(int)} gives the context of one of its elements, whose violations
 * name the position of the element in their target.
 *
 * <p>The context holds what differs from call to call. What stays the same, such as a {@link
 * java.time.Clock} or a repository, is a dependency of the rule: take it in the constructor, and a
 * test can pass a fixed clock.
 */
public final class RuleContext {

  /** No element: the context is not about one element of a collection. */
  private static final int NONE = -1;

  private final String ruleId;
  private final @Nullable Principal caller;
  private final Locale locale;

  /**
   * The positions, in the checked collection, of the inputs that {@code checkAll} receives, by
   * their index in its list; {@code null} where they are the same.
   */
  private final int @Nullable [] elements;

  /** The position, in the checked collection, of the element this context is about, or none. */
  private final int element;

  private RuleContext(
      String ruleId,
      @Nullable Principal caller,
      Locale locale,
      int @Nullable [] elements,
      int element) {
    this.ruleId = ruleId;
    this.caller = caller;
    this.locale = locale;
    this.elements = elements;
    this.element = element;
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
   * Returns a builder that starts from this context: the same rule, caller, locale and elements.
   * Use it to create the context of the same rule for another call.
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
   * Returns the locale of the call, for business logic that depends on the caller's language;
   * messages are resolved elsewhere. Do not normalize unique data, such as names, in this locale:
   * the same name could pass in one language and fail in another, as Turkish dotted and dotless i
   * do. Normalize it in one fixed locale.
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
    if (element != NONE) {
      throw new IllegalStateException(
          "this is the context of the element at ["
              + element
              + "] already; get the contexts of elements from the context that checkAll receives");
    }
    int position;
    if (elements == null) {
      if (index < 0) {
        throw new IllegalArgumentException("the index " + index + " is negative");
      }
      position = index;
    } else {
      if (index < 0 || index >= elements.length) {
        throw new IllegalArgumentException(
            "the index " + index + " is not that of one of the " + elements.length + " inputs");
      }
      position = elements[index];
    }
    return new RuleContext(ruleId, caller, locale, null, position);
  }

  /**
   * Returns the position, in the checked collection, of the element this context is about.
   *
   * @return the position, or empty if this context is not about an element
   */
  public OptionalInt elementIndex() {
    return element == NONE ? OptionalInt.empty() : OptionalInt.of(element);
  }

  /**
   * Starts a violation whose code is the rule id.
   *
   * @return a builder for the violation
   */
  public Violation.Builder violation() {
    return new Violation.Builder(ruleId, element);
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
    return new Violation.Builder(ruleId, code, element);
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
    if (element != NONE) {
      text.append(", element=").append(element);
    }
    return text.append(']').toString();
  }

  /**
   * Builds a {@link RuleContext}. Get one from {@link RuleContext#builder(String)}, or from {@link
   * RuleContext#toBuilder()} to start from an existing context. A builder is not safe for use by
   * multiple threads.
   */
  public static final class Builder {

    private final String ruleId;
    private @Nullable Principal caller;
    private Locale locale = Locale.ROOT;
    private int @Nullable [] elements;
    private int element = NONE;

    private Builder(String ruleId) {
      this.ruleId = Checks.requireRuleId(ruleId);
    }

    private Builder(RuleContext context) {
      // The id was checked when the context was built.
      ruleId = context.ruleId;
      caller = context.caller;
      locale = context.locale;
      elements = context.elements;
      element = context.element;
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
     * element(i)} is then about the element at {@code positions[i]}. Muster sets them when it
     * leaves out elements that failed a rule this rule requires; use it to test such a call. The
     * context built is not about an element.
     *
     * @param positions the positions, in ascending order
     * @return this builder
     * @throws IllegalArgumentException if a position is negative, or not greater than the one
     *     before
     */
    public Builder elements(int... positions) {
      var copy = requireNonNull(positions, "positions").clone();
      for (var i = 0; i < copy.length; i++) {
        if (copy[i] < 0 || (i > 0 && copy[i] <= copy[i - 1])) {
          throw new IllegalArgumentException(
              "the positions must be ascending and not negative: " + Arrays.toString(copy));
        }
      }
      elements = copy;
      element = NONE;
      return this;
    }

    /**
     * Builds the context. The builder can be used again; later changes do not affect contexts
     * already built.
     *
     * @return the context
     */
    public RuleContext build() {
      return new RuleContext(ruleId, caller, locale, elements, element);
    }
  }
}
