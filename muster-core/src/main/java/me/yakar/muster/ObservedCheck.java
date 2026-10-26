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

import java.time.Duration;
import java.util.List;
import me.yakar.muster.internal.rule.PreparedRule;
import me.yakar.muster.rule.Violation;
import me.yakar.muster.spi.CheckListener;
import me.yakar.muster.spi.CheckListener.Check;
import me.yakar.muster.spi.CheckListener.CheckEnd;
import me.yakar.muster.spi.CheckListener.Observer;
import me.yakar.muster.spi.CheckListener.Outcome;
import me.yakar.muster.spi.CheckListener.RuleEnd;
import org.jspecify.annotations.Nullable;

/**
 * One check as its listener observes it: the observer the listener returned, when the check and its
 * current rule started, and which rule has started and not ended. Lives on the thread that checks,
 * for one check, so nothing here is shared.
 */
final class ObservedCheck {

  private final Observer observer;
  private final long start;

  /** The rule that started and has not ended, or {@code null}. */
  private @Nullable PreparedRule rule;

  private long ruleStart;

  private ObservedCheck(Observer observer) {
    this.observer = observer;
    this.start = System.nanoTime();
  }

  /**
   * Tells the listener that a check starts. The event is built here rather than by the chain, which
   * keeps the loop of a single check small.
   *
   * @return the check as observed, or {@code null} if the listener observes nothing of it
   */
  static @Nullable ObservedCheck start(
      CheckListener listener,
      String name,
      Class<?> inputType,
      CheckMode mode,
      boolean collection,
      int size) {
    var observer = listener.checkStarted(new Check(name, inputType, mode, collection, size));
    return observer == null ? null : new ObservedCheck(observer);
  }

  /** Tells the observer that a rule starts, before it runs or is skipped. */
  void ruleStarted(PreparedRule rule) {
    observer.ruleStarted(rule.id());
    this.rule = rule;
    ruleStart = System.nanoTime();
  }

  /** Tells the observer that the rule that started did not run. */
  void ruleSkipped() {
    ruleEnded(Outcome.SKIPPED, List.of(), null);
  }

  /** Tells the observer that the rule that started found no violation. */
  void rulePassed() {
    ruleEnded(Outcome.PASSED, List.of(), null);
  }

  /** Tells the observer that the rule that started found violations. */
  void ruleFailed(List<Violation> violations) {
    ruleEnded(Outcome.FAILED, violations, null);
  }

  /** Tells the observer that the check passed. */
  void passed() {
    observer.checkEnded(new CheckEnd(Outcome.PASSED, took(start), List.of(), null));
  }

  /**
   * Tells the observer that the check ends with an exception: that the rule that started, if one
   * has not ended, ended with it, and that the check is rejected, if the exception is a {@link
   * BusinessRuleViolationException}, or ended with the exception otherwise. The observer's own
   * exceptions are added to the check's as suppressed.
   */
  void ended(Throwable exception) {
    if (rule != null) {
      try {
        ruleEnded(Outcome.ERROR, List.of(), exception);
      } catch (Throwable fromObserver) {
        suppress(exception, fromObserver);
      }
    }
    try {
      observer.checkEnded(
          exception instanceof BusinessRuleViolationException rejected
              ? new CheckEnd(Outcome.FAILED, took(start), rejected.violations(), null)
              : new CheckEnd(Outcome.ERROR, took(start), List.of(), exception));
    } catch (Throwable fromObserver) {
      suppress(exception, fromObserver);
    }
  }

  /**
   * Adds what the observer threw to the check's exception, unless it is that exception, or is
   * suppressed on it already: the observer may throw the exception it was told of, or one instance
   * every time, and an exception cannot suppress itself.
   */
  private static void suppress(Throwable exception, Throwable fromObserver) {
    if (fromObserver == exception) {
      return;
    }
    for (var suppressed : exception.getSuppressed()) {
      if (suppressed == fromObserver) {
        return;
      }
    }
    exception.addSuppressed(fromObserver);
  }

  private void ruleEnded(Outcome outcome, List<Violation> violations, @Nullable Throwable error) {
    // Closed before the observer is told, so that an exception it throws does not end it again.
    var ended = rule;
    rule = null;
    observer.ruleEnded(new RuleEnd(ended.id(), outcome, took(ruleStart), violations, error));
  }

  private static Duration took(long since) {
    return Duration.ofNanos(System.nanoTime() - since);
  }
}
