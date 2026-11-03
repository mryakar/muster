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
package example.plain;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.yakar.muster.BusinessRuleViolationException;
import me.yakar.muster.rule.Violation;
import me.yakar.muster.spi.MessageResolver;

/**
 * Answers a call that the rules rejected, as the handler of an HTTP request would: with a status
 * and an entry for each violation, which a JSON library writes. The starter answers the same with
 * problem details.
 */
final class Problems {

  private final MessageResolver messages;

  Problems(MessageResolver messages) {
    this.messages = messages;
  }

  // tag::problem[]
  /** The answer to a rejected call. */
  record Problem(int status, List<Entry> violations) {}

  /**
   * What the client learns of a violation, never its internal arguments. The target and the
   * category are null when the violation has none, and a JSON library leaves them out.
   */
  record Entry(
      String code, String message, String target, String category, Map<String, Object> args) {}

  Problem problem(BusinessRuleViolationException rejection, Locale locale) {
    var violations = rejection.violations();
    return new Problem(
        status(violations),
        violations.stream()
            .map(
                violation ->
                    new Entry(
                        violation.code(),
                        messages.resolve(violation, locale),
                        violation.target().orElse(null),
                        violation.category().map(Enum::name).orElse(null),
                        violation.clientArgs()))
            .toList());
  }

  // end::problem[]

  // tag::status[]
  /** Returns the status of the category the violations share, and 422 if they share none. */
  static int status(List<Violation> violations) {
    var category = violations.getFirst().category();
    if (violations.stream().anyMatch(violation -> !violation.category().equals(category))) {
      return 422;
    }
    return category
        .map(
            shared ->
                switch (shared) {
                  case FORBIDDEN -> 403;
                  case NOT_FOUND -> 404;
                  case CONFLICT -> 409;
                  case INVALID_STATE -> 422;
                })
        .orElse(422);
  }
  // end::status[]
}
