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

import spock.lang.Specification

class RuleCatalogSpec extends Specification {

  def 'with nothing excluded, a rule is not even looked up for what it requires'() {
    expect: 'the catalog has no rules, so a search would fail'
    new RuleCatalog([:], [] as Set).excludedRequirement(Object, [] as Set) == null
  }
}
