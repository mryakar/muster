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

import groovy.transform.CompileStatic
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import spock.lang.Specification

class RuleDefinitionSpec extends Specification {

  @Rule(id = 'vehicle.name-unique')
  static class NameUnique implements BusinessRule<String> {
    RuleResult check(String input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static class Unannotated implements BusinessRule<String> {
    RuleResult check(String input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static class SubclassOfRule extends NameUnique {}

  static class SubclassOfSubclass extends SubclassOfRule {}

  @CompileStatic
  static BusinessRule<String> lambda() {
    (String input, RuleContext ctx) -> RuleResult.passed()
  }

  static BusinessRule<String> anonymous() {
    new BusinessRule<String>() {
          RuleResult check(String input, RuleContext ctx) {
            RuleResult.passed()
          }
        }
  }

  def 'a definition holds the class, the id and the input type of a rule'() {
    expect:
    RuleDefinition.of(NameUnique) == new RuleDefinition(NameUnique, 'vehicle.name-unique', String)
  }

  def 'a rule needs @Rule'() {
    when:
    RuleDefinition.of(Unannotated)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "${Unannotated.name} is not annotated with @Rule"
  }

  def 'a lambda or an anonymous class cannot be a rule'() {
    expect: 'the classes are what the test means'
    ruleClass.hidden || ruleClass.anonymousClass

    when:
    RuleDefinition.of(ruleClass)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "${ruleClass.typeName} is not annotated with @Rule; a rule cannot be a lambda or an anonymous class, it is a named class with @Rule"

    where:
    ruleClass << [
      lambda().getClass(),
      anonymous().getClass()
    ]
  }

  def 'a subclass of a rule is told that @Rule is not inherited'() {
    when:
    RuleDefinition.of(ruleClass)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "${ruleClass.name} is not annotated with @Rule; @Rule is not inherited from ${NameUnique.name}"

    where:
    ruleClass << [
      SubclassOfRule,
      SubclassOfSubclass
    ]
  }

  def 'a rule id may be #id'() {
    given:
    def ruleClass = ruleWithId(id)

    expect:
    RuleDefinition.of(ruleClass).id() == id

    where:
    id << [
      'a',
      'task',
      'task2',
      'task.ready',
      'vehicle.name-unique',
      'plan.v2.capacity-sufficient',
      'a.1'
    ]
  }

  def 'a rule id may not be "#id"'() {
    given:
    def ruleClass = ruleWithId(id)

    when:
    RuleDefinition.of(ruleClass)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "the id '$id' of ${ruleClass.name} is invalid; an id starts with a lowercase letter and continues with lowercase letters, digits, and single dots or hyphens between them, as in 'vehicle.name-unique'"

    where:
    id << [
      '',
      'Task',
      'task.Ready',
      '1task',
      '.task',
      '-task',
      'task.',
      'task-',
      'task..ready',
      'task.-ready',
      'task_ready',
      'task ready',
      'görev'
    ]
  }

  private static Class<?> ruleWithId(String id) {
    new GroovyClassLoader(RuleDefinitionSpec.classLoader).parseClass("""
      import me.yakar.muster.rule.*
      @Rule(id = '$id')
      class GeneratedRule implements BusinessRule<String> {
        RuleResult check(String input, RuleContext ctx) { RuleResult.passed() }
      }
      """)
  }
}
