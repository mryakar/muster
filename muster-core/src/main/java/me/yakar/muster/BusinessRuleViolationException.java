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
 * Thrown when a check finds violated business rules. In {@link CheckMode#FAIL_FAST} mode, the
 * default, it holds the violations of the first violated rule; in {@link CheckMode#COLLECT_ALL}
 * mode, those of every violated rule.
 *
 * <p>A violation is an expected outcome, so the exception has no stack trace; it keeps the
 * exceptions suppressed on it, such as the failure of a resource closed while it propagates. Its
 * message lists the violation codes, never argument values. Map {@link #violations()} to a response
 * type of your own rather than returning them as they are.
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
    super(null, null, true, false);
    var copy = List.copyOf(requireNonNull(violations, "violations"));
    if (copy.isEmpty()) {
      throw new IllegalArgumentException("the exception needs at least one violation");
    }
    this.violations = copy;
  }

  /**
   * Returns the number of violations and their codes, never argument values.
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
