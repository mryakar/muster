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

  @Rule(id = 'task.ready')
  static class ChecksAll implements BusinessRule<String> {
    RuleResult check(String input, RuleContext ctx) {
      RuleResult.passed()
    }

    RuleResult checkAll(List<? extends String> inputs, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  @Rule(id = 'task.ready')
  static class InheritsChecksAll extends ChecksAll {}

  /** Public methods that look like checkAll, but are not it. */
  @Rule(id = 'task.ready')
  static class LooksLikeChecksAll implements BusinessRule<String> {
    RuleResult check(String input, RuleContext ctx) {
      RuleResult.passed()
    }

    RuleResult checkAll(String input) {
      RuleResult.passed()
    }

    RuleResult audit(List<String> inputs, RuleContext ctx) {
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

  def 'a rule checks a collection itself if its class #how'() {
    expect:
    PreparedRule.of(RuleDefinition.of(ruleClass), ruleClass.getConstructor().newInstance()).checksAll() == checksAll

    where:
    how                                        | ruleClass || checksAll
    'overrides checkAll'                       | ChecksAll || true
    'inherits an override'                     | InheritsChecksAll || true
    'does not override it'                     | TaskReady || false
    'has methods that only look like checkAll' | LooksLikeChecksAll || false
  }

  def 'a prepared rule is told once that it checks elements one by one'() {
    given:
    def prepared = PreparedRule.of(definition, new TaskReady())

    expect:
    prepared.firstOneByOne()
    !prepared.firstOneByOne()
    !prepared.firstOneByOne()
  }

  def 'a prepared rule shows its definition, instance and id, and names its rule'() {
    given:
    def rule = new TaskReady()
    def prepared = PreparedRule.of(definition, rule)

    expect:
    prepared.definition().is(definition)
    prepared.instance().is(rule)
    prepared.id() == 'task.ready'
    prepared.toString() == 'PreparedRule[task.ready]'
  }
}
