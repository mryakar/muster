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
import org.jspecify.annotations.Nullable;

/**
 * The outcome of a rule check: passed, possibly with a value, or failed with one or more
 * violations. Rules return a result instead of throwing; Muster throws one {@link
 * me.yakar.muster.BusinessRuleViolationException} when the check ends.
 *
 * <p>A rule that checks a collection at once, in {@link BusinessRule#checkAll}, may also return the
 * result of each element, with {@link #ofElements(List)}.
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
   * Returns the result of a {@link ValueRule} that passed, with the value it leaves for the rules
   * that require it.
   *
   * @param value the value, of the value type of the rule
   * @return the passed result
   */
  static RuleResult passed(Object value) {
    return new Passed(requireNonNull(value, "value"));
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

  /**
   * Returns the result of a rule that checked a collection at once, in {@link
   * BusinessRule#checkAll}, as the result of each of its inputs: passed, with a value for a {@link
   * ValueRule}, or failed with violations created from the context of that element, {@code
   * ctx.element(i)}.
   *
   * {@snippet :
   * var results = new ArrayList<RuleResult>();
   * for (var i = 0; i < inputs.size(); i++) {
   *   var vehicle = found.get(inputs.get(i).vehicleId());
   *   results.add(
   *       vehicle != null
   *           ? RuleResult.passed(vehicle)
   *           : RuleResult.failed(ctx.element(i).violation().build()));
   * }
   * return RuleResult.ofElements(results);
   * }
   *
   * @param results the result of each input, in the order of the inputs that {@code checkAll}
   *     receives
   * @return the result of the collection
   * @throws IllegalArgumentException if {@code results} is empty or holds the result of a
   *     collection
   */
  static RuleResult ofElements(List<RuleResult> results) {
    return new Elements(results);
  }

  /**
   * A rule passed.
   *
   * @param value the value a {@link ValueRule} leaves, or {@code null} for any other rule
   */
  record Passed(@Nullable Object value) implements RuleResult {

    private static final Passed INSTANCE = new Passed();

    /** Creates the result of a rule that passed without a value. */
    public Passed() {
      this(null);
    }
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

  /**
   * A rule checked a collection at once and gave the result of each of its inputs.
   *
   * @param results the result of each input, in the order of the inputs, never empty; each is
   *     {@link Passed} or {@link Failed}
   */
  record Elements(List<RuleResult> results) implements RuleResult {

    /**
     * Creates the result of a collection from the result of each input.
     *
     * @throws IllegalArgumentException if {@code results} is empty or holds the result of a
     *     collection
     */
    public Elements {
      results = List.copyOf(requireNonNull(results, "results"));
      if (results.isEmpty()) {
        throw new IllegalArgumentException("the result of a collection needs a result per input");
      }
      for (var result : results) {
        if (result instanceof Elements) {
          throw new IllegalArgumentException(
              "the result of an input is passed or failed, not the result of a collection");
        }
      }
    }
  }
}
