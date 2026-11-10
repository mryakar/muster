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

import me.yakar.muster.rule.RuleContext
import spock.lang.Specification

class ContextsSpec extends Specification {

  def 'the factory of contexts is registered once, when RuleContext is initialized'() {
    given:
    RuleContext.forRule('task.ready')

    when:
    Contexts.register { prepared, locale, caller, reads, values -> prepared }

    then:
    def e = thrown(IllegalStateException)
    e.message == 'the factory of contexts is registered already'
  }

  def 'the factory of contexts is not null'() {
    when:
    Contexts.register(null)

    then:
    def e = thrown(NullPointerException)
    e.message == 'factory'
  }
}
