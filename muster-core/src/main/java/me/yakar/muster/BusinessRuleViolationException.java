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
import static java.util.stream.Collectors.joining;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serial;
import java.util.List;
import me.yakar.muster.rule.Violation;

/**
 * Thrown when a call violates one or more business rules. In {@link CheckMode#FAIL_FAST} mode, the
 * default, it holds the violations of the first violated rule only; in {@link
 * CheckMode#COLLECT_ALL} mode, those of every violated rule.
 *
 * <p>A violation is an expected outcome, not a programming error, so this exception has no stack
 * trace. Its message lists the violation codes only, never argument values; it may end up in front
 * of a client.
 *
 * <p>Do not return {@link #violations()} as a response body as it is: map each violation to a
 * response type of your own.
 */
public final class BusinessRuleViolationException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /** The violations. Always a {@link List#copyOf} result, which is serializable. */
  @SuppressWarnings("serial")
  private final List<Violation> violations;

  /**
   * Creates the exception.
   *
   * @param violations the violations, in the order they were found
   * @throws IllegalArgumentException if {@code violations} is empty
   */
  public BusinessRuleViolationException(List<Violation> violations) {
    super(null, null, false, false);
    var copy = List.copyOf(requireNonNull(violations, "violations"));
    if (copy.isEmpty()) {
      throw new IllegalArgumentException("the exception needs at least one violation");
    }
    this.violations = copy;
  }

  /**
   * Returns the number of violations and their codes, never argument values. It is built when asked
   * for: a caller that maps the violations to a response never needs it.
   *
   * @return the message
   */
  @Override
  public String getMessage() {
    var noun =
        violations.size() == 1 ? " business rule violation: " : " business rule violations: ";
    return violations.size()
        + noun
        + violations.stream().map(Violation::code).collect(joining(", "));
  }

  /**
   * Returns the violations, in the order they were found.
   *
   * @return the violations, never empty
   */
  public List<Violation> violations() {
    return violations;
  }

  /**
   * Rebuilds the exception, so that a deserialized one keeps the guarantees of the constructor: at
   * least one violation, none of them {@code null}, in a list that cannot be changed.
   *
   * @return the rebuilt exception
   * @throws InvalidObjectException if the stream breaks those guarantees
   */
  @Serial
  private Object readResolve() throws ObjectStreamException {
    try {
      return new BusinessRuleViolationException(violations);
    } catch (RuntimeException e) {
      var invalid = new InvalidObjectException(e.getMessage());
      invalid.initCause(e);
      throw invalid;
    }
  }
}
