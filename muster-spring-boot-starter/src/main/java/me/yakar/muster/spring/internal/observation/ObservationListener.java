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
package me.yakar.muster.spring.internal.observation;

import static java.util.Objects.requireNonNull;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Locale;
import me.yakar.muster.spi.CheckListener;
import org.jspecify.annotations.Nullable;

/**
 * Opens an observation {@code muster.check} for each check, with the name of the binding, and a
 * child observation {@code muster.rule} for each of its rules, with the id of the rule; both get
 * the outcome when they end, and the exception if they end with an error. Each observation is in
 * scope while it runs, so what a rule calls is observed under it. The names and the tags are fixed;
 * an application narrows them with an {@code ObservationPredicate} of its registry.
 *
 * <p>The listener holds the registry only; the observations of a check live in the observer of that
 * check, on the thread that checks.
 */
final class ObservationListener implements CheckListener {

  static final String CHECK = "muster.check";
  static final String RULE = "muster.rule";

  private final ObservationRegistry registry;

  ObservationListener(ObservationRegistry registry) {
    this.registry = registry;
  }

  @Override
  public @Nullable Observer checkStarted(Check check) {
    var observation =
        Observation.createNotStarted(CHECK, registry)
            .contextualName(check.name())
            .lowCardinalityKeyValue("name", check.name());
    // A no-op observation, as when a predicate of the registry leaves muster.check out, is not
    // worth an observer: nothing of the check is observed.
    return observation.isNoop() ? null : new ObservedCheck(registry, observation.start());
  }

  /** The observations of one check: the check's own, in scope, and the rule that runs. */
  private static final class ObservedCheck implements Observer {

    private final ObservationRegistry registry;
    private final Observation check;
    private final Observation.Scope checkScope;
    private @Nullable Observation rule;
    private Observation.@Nullable Scope ruleScope;

    ObservedCheck(ObservationRegistry registry, Observation check) {
      this.registry = registry;
      this.check = check;
      this.checkScope = check.openScope();
    }

    @Override
    public void ruleStarted(String ruleId) {
      // The open scope of the check makes it the parent already; said anyway, for the reader.
      rule =
          Observation.createNotStarted(RULE, registry)
              .parentObservation(check)
              .contextualName(ruleId)
              .lowCardinalityKeyValue("rule", ruleId)
              .start();
      ruleScope = rule.openScope();
    }

    @Override
    public void ruleEnded(RuleEnd end) {
      var ended = requireNonNull(rule, "the rule started");
      var scope = requireNonNull(ruleScope, "the rule started");
      // Cleared first, so that a rule is stopped once, whatever a handler throws.
      rule = null;
      ruleScope = null;
      stop(ended, scope, end.outcome(), end.error());
    }

    @Override
    public void checkEnded(CheckEnd end) {
      stop(check, checkScope, end.outcome(), end.error());
    }

    /** Tags an observation with its outcome, and its error if it has one, and stops it. */
    private static void stop(
        Observation observation,
        Observation.Scope scope,
        Outcome outcome,
        @Nullable Throwable error) {
      observation.lowCardinalityKeyValue("outcome", outcome.name().toLowerCase(Locale.ROOT));
      if (error != null) {
        observation.error(error);
      }
      scope.close();
      observation.stop();
    }
  }
}
