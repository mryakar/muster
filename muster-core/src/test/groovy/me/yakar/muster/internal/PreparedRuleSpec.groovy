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
package me.yakar.muster.internal

import java.security.Principal
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import spock.lang.Specification

class PreparedRuleSpec extends Specification {

  @Rule(id = 'task.ready')
  static class TaskReady implements BusinessRule<String> {
    RuleResult check(String input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  def definition = RuleDefinition.of(TaskReady)

  def 'a prepared rule has a context of its own, with no caller and the root locale'() {
    when:
    def prepared = PreparedRule.of(definition, new TaskReady())

    then:
    prepared.context().ruleId() == 'task.ready'
    prepared.context().caller().empty
    prepared.context().locale() == Locale.ROOT
  }

  def 'the context of #what is rejected'() {
    when:
    new PreparedRule(definition, new TaskReady(), context)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "the prepared context of rule 'task.ready' must be for that rule and have no caller"

    where:
    what                    | context
    'another rule'          | RuleContext.forRule('task.exists')
    'a call with a caller'  | RuleContext.builder('task.ready').caller({
      'amo'
    } as Principal).build()
  }
}
