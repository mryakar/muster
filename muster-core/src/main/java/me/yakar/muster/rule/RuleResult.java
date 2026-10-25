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

import java.util.List;

/**
 * The outcome of checking a rule: it either passed or failed with one or more violations.
 *
 * <p>Rules return a result instead of throwing. Muster collects the results and throws a single
 * {@link me.yakar.muster.BusinessRuleViolationException} when the check ends; in the default {@link
 * me.yakar.muster.CheckMode#FAIL_FAST} mode, the check ends at the first failed rule, and later
 * rules do not run.
 */
public sealed interface RuleResult {

  /**
   * Returns the result of a rule that passed.
   *
   * @return the passed result
   */
  static RuleResult passed() {
    return Passed.INSTANCE;
  }

  /**
   * Returns the result of a rule that failed.
   *
   * @param first the first violation
   * @param more further violations, in the order the rule found them
   * @return the failed result
   */
  static RuleResult failed(Violation first, Violation... more) {
    // List.of keeps a single violation without an array, and Failed keeps the list as it is.
    var violations = new Violation[more.length + 1];
    violations[0] = first;
    System.arraycopy(more, 0, violations, 1, more.length);
    return new Failed(List.of(violations));
  }

  /**
   * Returns the result of a rule that failed.
   *
   * @param violations the violations, in the order the rule found them
   * @return the failed result
   * @throws IllegalArgumentException if {@code violations} is empty
   */
  static RuleResult failed(List<Violation> violations) {
    return new Failed(violations);
  }

  /** A rule passed. */
  record Passed() implements RuleResult {

    private static final Passed INSTANCE = new Passed();
  }

  /**
   * A rule failed.
   *
   * @param violations the violations, never empty
   */
  record Failed(List<Violation> violations) implements RuleResult {

    /**
     * Creates a failed result.
     *
     * @throws IllegalArgumentException if {@code violations} is empty
     */
    public Failed {
      violations = List.copyOf(requireNonNull(violations, "violations"));
      if (violations.isEmpty()) {
        throw new IllegalArgumentException("a failed result needs at least one violation");
      }
    }
  }
}
