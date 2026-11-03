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
package me.yakar.muster.internal.rule

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide

/** The rule ids that Muster accepts, against the format written as a regular expression. */
class RuleIdsProperties {

  private static final String FORMAT = /[a-z][a-z0-9]*([.-][a-z0-9]+)*/

  @Provide
  Arbitrary<String> candidates() {
    // Mostly the characters of ids, so that valid and invalid ones both come up often.
    Arbitraries.strings().withChars('ab9.-_A ').ofMaxLength(8)
  }

  @Property
  void 'an id is valid exactly when it has the format'(@ForAll('candidates') String id) {
    assert RuleIds.isValid(id) == (id ==~ FORMAT)
  }
}
