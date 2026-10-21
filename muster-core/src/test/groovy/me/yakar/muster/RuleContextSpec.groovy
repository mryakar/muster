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
package me.yakar.muster

import spock.lang.Specification

class RuleContextSpec extends Specification {

  def 'a context knows the rule it belongs to'() {
    expect:
    RuleContext.forRule('task.ready').ruleId() == 'task.ready'
  }

  def 'a rule id must not be blank'() {
    when:
    RuleContext.forRule(ruleId)

    then:
    def e = thrown(exception)
    e.message == message

    where:
    ruleId || exception                | message
    '' || IllegalArgumentException | 'ruleId must not be blank'
    ' ' || IllegalArgumentException | 'ruleId must not be blank'
    null || NullPointerException     | 'ruleId'
  }
}
