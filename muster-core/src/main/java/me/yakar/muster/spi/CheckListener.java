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
package me.yakar.muster.spi;

import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.List;
import me.yakar.muster.CheckMode;
import me.yakar.muster.rule.Violation;
import org.jspecify.annotations.Nullable;

/**
 * Observes the checks of a {@code Muster}: when a check starts and ends, and when each of its rules
 * starts and ends, with its outcome and how long it took. Register one with {@link
 * me.yakar.muster.Muster.Builder#listener}, for example to time the rules or to trace a check.
 *
 * <p>Muster tells a listener on the thread that checks, in the order things happen: {@link
 * #checkStarted} once, then for each rule of the chain, in the order the rules run, {@link
 * Observer#ruleStarted} and {@link Observer#ruleEnded}, then {@link Observer#checkEnded}. Each
 * check gets an {@link Observer} of its own, so a listener keeps no state to tell one check from
 * another. The listener itself is shared by every check and must be thread-safe. A check of an
 * empty collection runs no rule, and a listener is not told of it.
 *
 * <p>A listener that throws stops the check, as a rule that throws does. If the check is ending
 * with an exception already, that exception propagates and the listener's is added to it as
 * suppressed. An observer that another listener returned for a check that a listener refused to
 * start is told nothing more. Without a listener, a check pays one null check.
 */
@FunctionalInterface
public interface CheckListener {

  /**
   * Called when a check starts.
   *
   * @param check what is checked
   * @return the observer of this check, or {@code null} to observe nothing of it
   */
  @Nullable Observer checkStarted(Check check);

  /**
   * Observes one check, on the thread that checks. Each method does nothing unless it is
   * overridden.
   */
  interface Observer {

    /**
     * Called before a rule runs, or is skipped.
     *
     * @param ruleId the id of the rule
     */
    default void ruleStarted(String ruleId) {}

    /**
     * Called after a rule ran, or was skipped.
     *
     * @param end how the rule ended
     */
    default void ruleEnded(RuleEnd end) {}

    /**
     * Called when the check ends, after its last rule.
     *
     * @param end how the check ended
     */
    default void checkEnded(CheckEnd end) {}
  }

  /**
   * A check that starts.
   *
   * @param name the name of the binding, or the name of the type for a chain, a binding without a
   *     name, or a check by type
   * @param inputType the type of the input, or of the elements of a checked collection
   * @param mode the mode of the check
   * @param collection whether a collection is checked
   * @param size the number of inputs: 1 for a single input, or the size of the collection
   */
  record Check(String name, Class<?> inputType, CheckMode mode, boolean collection, int size) {

    /** Creates a check. */
    public Check {
      requireNonNull(name, "name");
      requireNonNull(inputType, "inputType");
      requireNonNull(mode, "mode");
    }
  }

  /**
   * How a rule ended.
   *
   * @param ruleId the id of the rule
   * @param outcome the outcome
   * @param took the time since the rule started
   * @param violations the violations the rule found, or none
   * @param error what the rule threw, or what Muster threw checking it, if the outcome is {@link
   *     Outcome#ERROR}
   */
  record RuleEnd(
      String ruleId,
      Outcome outcome,
      Duration took,
      List<Violation> violations,
      @Nullable Throwable error) {

    /** Creates the end of a rule. */
    public RuleEnd {
      requireNonNull(ruleId, "ruleId");
      requireNonNull(outcome, "outcome");
      requireNonNull(took, "took");
      violations = List.copyOf(requireNonNull(violations, "violations"));
    }
  }

  /**
   * How a check ended.
   *
   * @param outcome {@link Outcome#PASSED}, {@link Outcome#FAILED} or {@link Outcome#ERROR}; a check
   *     is rejected with the {@code BusinessRuleViolationException} that propagates from it, also
   *     when a rule threw it, as a rule that checks another input may
   * @param took the time since the check started
   * @param violations the violations the check is rejected with, or none
   * @param error what the check threw, if the outcome is {@link Outcome#ERROR}
   */
  record CheckEnd(
      Outcome outcome, Duration took, List<Violation> violations, @Nullable Throwable error) {

    /** Creates the end of a check. */
    public CheckEnd {
      requireNonNull(outcome, "outcome");
      requireNonNull(took, "took");
      violations = List.copyOf(requireNonNull(violations, "violations"));
    }
  }

  /** How a rule, or a check, ended. */
  enum Outcome {
    /** The rule found no violation, or every rule of the check passed. */
    PASSED,
    /** The rule found violations, or the check is rejected with the violations of its rules. */
    FAILED,
    /**
     * The rule did not run: it does not apply to the call, a rule it requires did not pass, or, for
     * a rule of a whole collection, not every element passed the rules it requires.
     */
    SKIPPED,
    /**
     * The rule threw, returned {@code null} or reported a violation of another rule, or the check
     * ended with an exception that is not a rejection; the listener gets the exception.
     */
    ERROR
  }
}
