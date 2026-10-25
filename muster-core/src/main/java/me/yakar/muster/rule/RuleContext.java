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
import java.util.Locale;
import java.util.Optional;
import me.yakar.muster.internal.RuleDefinition;
import me.yakar.muster.internal.RuleIds;
import org.jspecify.annotations.Nullable;

/**
 * What a rule knows about the call it checks, besides its input: who makes the call and in which
 * locale.
 *
 * <p>Each rule receives its own context. Rules create their violations through it, so every
 * violation carries the id of the rule that reported it.
 *
 * <p>The context holds what differs from call to call. What stays the same, such as a {@link
 * java.time.Clock} or a repository, is a dependency of the rule: take it in the constructor, and a
 * test can pass a fixed clock.
 */
public final class RuleContext {

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
   * Returns a builder that starts from this context: the same rule, caller and locale. Use it to
   * create the context of the same rule for another call. The rule id is not checked again, since
   * it was checked when this context was built.
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
   * Returns the locale of the call. Messages are resolved elsewhere; use the locale for business
   * logic that depends on the caller's language. Do not use it to normalize data that must stay
   * unique: compared without regard to case in the caller's locale, the same name can pass for one
   * language and fail for another, as Turkish dotted and dotless i do. Normalize stored data in one
   * fixed locale instead.
   *
   * @return the locale
   */
  public Locale locale() {
    return locale;
  }

  /**
   * Starts a violation whose code is the rule id.
   *
   * @return a builder for the violation
   */
  public Violation.Builder violation() {
    return new Violation.Builder(ruleId);
  }

  /**
   * Starts a violation with the given code. Use it when a rule reports more than one kind of
   * problem.
   *
   * @param code the stable, machine-readable code of the violation
   * @return a builder for the violation
   * @throws IllegalArgumentException if {@code code} is blank or contains whitespace, a space,
   *     control or format character, or an unpaired surrogate
   */
  public Violation.Builder violation(String code) {
    return new Violation.Builder(ruleId, code);
  }

  /**
   * Describes this context for developers and logs: the rule id, the caller's name if there is a
   * caller, and the locale.
   *
   * @return the description
   */
  @Override
  public String toString() {
    var text = new StringBuilder("RuleContext[ruleId=").append(ruleId);
    if (caller != null) {
      text.append(", caller=").append(caller.getName());
    }
    return text.append(", locale=").append(locale.toLanguageTag()).append(']').toString();
  }

  /**
   * Builds a {@link RuleContext}. Get one from {@link RuleContext#builder(String)}, or from {@link
   * RuleContext#toBuilder()} to start from an existing context.
   */
  public static final class Builder {

    private final String ruleId;
    private @Nullable Principal caller;
    private Locale locale = Locale.ROOT;

    private Builder(String ruleId) {
      requireNonNull(ruleId, "ruleId");
      if (!RuleIds.isValid(ruleId)) {
        throw new IllegalArgumentException(
            "the rule id '" + ruleId + "' is invalid; " + RuleIds.FORMAT);
      }
      this.ruleId = ruleId;
    }

    private Builder(RuleContext context) {
      ruleId = context.ruleId;
      caller = context.caller;
      locale = context.locale;
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
     * Builds the context. The builder can be used again; later changes do not affect contexts
     * already built.
     *
     * @return the context
     */
    public RuleContext build() {
      return new RuleContext(ruleId, caller, locale);
    }
  }
}
