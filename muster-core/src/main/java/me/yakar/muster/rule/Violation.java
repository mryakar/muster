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
import static me.yakar.muster.rule.Checks.requireArgumentName;
import static me.yakar.muster.rule.Checks.requireKey;
import static me.yakar.muster.rule.Checks.requireNonBlank;
import static me.yakar.muster.rule.Checks.requireRuleId;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

/**
 * A business rule that a call does not satisfy. Rules create violations through {@link
 * RuleContext#violation()}.
 *
 * <p>A violation carries a stable code and named arguments, not a message; the edge of the
 * application resolves the message in the locale of the call. Arguments come in two kinds:
 *
 * <ul>
 *   <li><b>Client arguments</b> fill the message template and are sent to the client.
 *   <li><b>Internal arguments</b> are for code and logs only and never reach a message or a
 *       response. Java serialization drops them, so a deserialized copy of a violation that had
 *       them does not equal the original.
 * </ul>
 *
 * <p>Violations are immutable. Map them to a response type of your own: JSON mappers find nothing
 * to write in a violation.
 */
public final class Violation implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** The arguments of every violation that has none of a kind, so that it allocates no map. */
  private static final SequencedMap<String, Object> NO_ARGS =
      Collections.unmodifiableSequencedMap(new LinkedHashMap<>());

  /** The id of the rule that reported the violation. */
  private final String ruleId;

  /** The stable, machine-readable code. */
  private final String code;

  /**
   * The client arguments. Always an unmodifiable {@link LinkedHashMap}, which is serializable as
   * long as the values are.
   */
  @SuppressWarnings("serial")
  private final SequencedMap<String, Object> clientArgs;

  private final transient SequencedMap<String, Object> internalArgs;

  /** The field of the input the violation is about, or {@code null}. */
  private final @Nullable String target;

  /** The kind of problem, or {@code null}. */
  private final @Nullable Category category;

  private Violation(Builder builder) {
    ruleId = builder.ruleId;
    code = builder.code;
    clientArgs = copy(builder.clientArgs);
    internalArgs = copy(builder.internalArgs);
    target = builder.target;
    category = builder.category;
  }

  private static SequencedMap<String, Object> copy(@Nullable SequencedMap<String, Object> args) {
    return args == null ? NO_ARGS : Collections.unmodifiableSequencedMap(new LinkedHashMap<>(args));
  }

  /**
   * Returns the id of the rule that reported this violation.
   *
   * @return the rule id
   */
  public String ruleId() {
    return ruleId;
  }

  /**
   * Returns the stable, machine-readable code of this violation. Unless the rule chose another one,
   * it is the rule id.
   *
   * @return the code
   */
  public String code() {
    return code;
  }

  /**
   * Returns the arguments that fill the message template and are sent to the client, in the order
   * they were added.
   *
   * @return the client arguments; empty if there are none
   */
  public SequencedMap<String, Object> clientArgs() {
    return clientArgs;
  }

  /**
   * Returns the arguments for the application only, in the order they were added. They never reach
   * a message or a response.
   *
   * @return the internal arguments; empty if there are none
   */
  public SequencedMap<String, Object> internalArgs() {
    return internalArgs;
  }

  /**
   * Returns the field of the input this violation is about, such as {@code name}.
   *
   * @return the target, if the violation is about a single field
   */
  public Optional<String> target() {
    return Optional.ofNullable(target);
  }

  /**
   * Returns the kind of problem this violation reports.
   *
   * @return the category, if the rule set one
   */
  public Optional<Category> category() {
    return Optional.ofNullable(category);
  }

  /**
   * Returns how serious this violation is.
   *
   * @return the severity
   */
  public Severity severity() {
    // Every violation rejects the call until non-blocking ones exist.
    return Severity.ERROR;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return other instanceof Violation that
        && ruleId.equals(that.ruleId)
        && code.equals(that.code)
        && clientArgs.equals(that.clientArgs)
        && internalArgs.equals(that.internalArgs)
        && Objects.equals(target, that.target)
        && category == that.category;
  }

  @Override
  public int hashCode() {
    return Objects.hash(ruleId, code, clientArgs, internalArgs, target, category);
  }

  /**
   * Describes this violation for developers and logs, internal arguments included. Do not send it
   * to clients.
   *
   * @return the description
   */
  @Override
  public String toString() {
    var text = new StringBuilder("Violation[ruleId=").append(ruleId).append(", code=").append(code);
    text.append(", clientArgs=").append(clientArgs).append(", internalArgs=").append(internalArgs);
    if (target != null) {
      text.append(", target=").append(target);
    }
    if (category != null) {
      text.append(", category=").append(category);
    }
    return text.append(", severity=").append(severity()).append(']').toString();
  }

  /**
   * Rebuilds the violation, so that a deserialized one is checked as a built one is and has no
   * internal arguments.
   *
   * @return the rebuilt violation
   * @throws InvalidObjectException if the stream holds what no built violation can
   */
  @Serial
  private Object readResolve() throws ObjectStreamException {
    try {
      var builder = new Builder(requireRuleId(ruleId), code);
      clientArgs.forEach(builder::clientArg);
      if (target != null) {
        builder.target(target);
      }
      if (category != null) {
        builder.category(category);
      }
      return builder.build();
    } catch (RuntimeException e) {
      var invalid = new InvalidObjectException(e.getMessage());
      invalid.initCause(e);
      throw invalid;
    }
  }

  /**
   * Builds a {@link Violation}. Get one from {@link RuleContext#violation()}.
   *
   * <p>An argument name is not blank and contains no whitespace, control or format character,
   * unpaired surrogate or brace. A name is used once, across client and internal arguments.
   *
   * <p>A builder is not safe for use by multiple threads.
   */
  public static final class Builder {

    private final String ruleId;
    private final String code;
    // Created with the first argument of their kind, since most violations have none.
    private @Nullable SequencedMap<String, Object> clientArgs;
    private @Nullable SequencedMap<String, Object> internalArgs;
    private @Nullable String target;
    private @Nullable Category category;

    // Both constructors get a rule id that is already checked, and a valid rule id is a valid code.
    Builder(String ruleId) {
      this.ruleId = ruleId;
      this.code = ruleId;
    }

    Builder(String ruleId, String code) {
      this.ruleId = ruleId;
      this.code = requireKey(code, "code");
    }

    /**
     * Adds an argument that fills the message template and is sent to the client. For Java
     * serialization, the value must be {@link Serializable}.
     *
     * @param name the name the template refers to, as in {@code {name}}
     * @param value the value
     * @return this builder
     * @throws IllegalArgumentException if {@code name} is already used or is not a valid argument
     *     name
     */
    public Builder clientArg(String name, Object value) {
      requireNew(name, value);
      if (clientArgs == null) {
        clientArgs = new LinkedHashMap<>();
      }
      clientArgs.put(name, value);
      return this;
    }

    /**
     * Adds an argument for the application only. It never reaches a message or a response.
     *
     * @param name the name
     * @param value the value
     * @return this builder
     * @throws IllegalArgumentException if {@code name} is already used or is not a valid argument
     *     name
     */
    public Builder internalArg(String name, Object value) {
      requireNew(name, value);
      if (internalArgs == null) {
        internalArgs = new LinkedHashMap<>();
      }
      internalArgs.put(name, value);
      return this;
    }

    private void requireNew(String name, Object value) {
      requireArgumentName(name);
      requireNonNull(value, () -> "value of argument '" + name + "'");
      if (clientArgs != null && clientArgs.containsKey(name)) {
        throw new IllegalArgumentException("'" + name + "' is already a client argument");
      }
      if (internalArgs != null && internalArgs.containsKey(name)) {
        throw new IllegalArgumentException("'" + name + "' is already an internal argument");
      }
    }

    /**
     * Sets the field of the input the violation is about.
     *
     * @param target the field, such as {@code name}
     * @return this builder
     * @throws IllegalArgumentException if {@code target} is blank
     */
    public Builder target(String target) {
      this.target = requireNonBlank(target, "target");
      return this;
    }

    /**
     * Sets the kind of problem the violation reports.
     *
     * @param category the category
     * @return this builder
     */
    public Builder category(Category category) {
      this.category = requireNonNull(category, "category");
      return this;
    }

    /**
     * Builds the violation. The builder can be used again; later changes do not affect violations
     * already built.
     *
     * @return the violation
     */
    public Violation build() {
      return new Violation(this);
    }
  }
}
