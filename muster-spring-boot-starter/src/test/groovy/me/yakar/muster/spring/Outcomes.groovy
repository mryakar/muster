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
package me.yakar.muster.spring

import me.yakar.muster.BusinessRuleViolationException

/** What a call inside a context comes to, where Spock's {@code thrown()} cannot see it. */
final class Outcomes {

  /** Returns the codes of the violations that the call throws, or none if it passes. */
  static List<String> violated(Closure call) {
    try {
      call()
      []
    } catch (BusinessRuleViolationException e) {
      e.violations()*.code()
    }
  }
}
