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

import groovy.transform.CompileStatic
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.RequiresRule
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

  @Rule(id = 'vehicle.name-unique-on-rename')
  @RequiresRule([NameUnique, Unannotated])
  static class Requiring implements BusinessRule<String> {
    RuleResult check(String input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  @Rule(id = 'vehicle.twice')
  @RequiresRule([NameUnique, Unannotated, NameUnique])
  static class RequiringTwice implements BusinessRule<String> {
    RuleResult check(String input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  @RequiresRule(NameUnique)
  static abstract class RequiringBase implements BusinessRule<String> {
    RuleResult check(String input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  @Rule(id = 'vehicle.from-base')
  static class FromBase extends RequiringBase {}

  static abstract class Middle extends RequiringBase {}

  @Rule(id = 'vehicle.from-grandparent')
  static class FromGrandparent extends Middle {}

  @RequiresRule(NameUnique)
  static interface RequiringInterface extends BusinessRule<String> {}

  @Rule(id = 'vehicle.from-interface')
  static class FromInterface implements RequiringInterface {
    RuleResult check(String input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static abstract class BaseWithInterface implements RequiringInterface {}

  @Rule(id = 'vehicle.from-interface-of-base')
  static class FromInterfaceOfBase extends BaseWithInterface {
    RuleResult check(String input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  @Rule(id = 'vehicle.own-requirements')
  @RequiresRule(Requiring)
  static class OwnRequirements extends RequiringBase {}

  @Rule(id = 'vehicle.requires-nothing')
  @RequiresRule([])
  static class RequiresNothing extends RequiringBase {}

  static abstract class PlainBase implements BusinessRule<String> {}

  @Rule(id = 'vehicle.plain')
  static class Plain extends PlainBase implements BusinessRule<String> {
    RuleResult check(String input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  @Rule(id = 'Task')
  static class BadId implements BusinessRule<String> {
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
    RuleDefinition.of(NameUnique) == new RuleDefinition(NameUnique, 'vehicle.name-unique', String, [])
  }

  def 'a definition lists the rules a rule requires, in its order'() {
    expect:
    RuleDefinition.of(Requiring).requires() == [NameUnique, Unannotated]
  }

  def 'a rule whose #where declares @RequiresRule must declare it too'() {
    when:
    RuleDefinition.of(ruleClass)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "${ruleClass.name} does not declare @RequiresRule, but ${declaring.name} does; @RequiresRule is not inherited, so declare the rules ${ruleClass.simpleName} requires on it"

    where:
    where                     | ruleClass || declaring
    'superclass'              | FromBase || RequiringBase
    'grandparent'             | FromGrandparent || RequiringBase
    'interface'               | FromInterface || RequiringInterface
    'superclass\'s interface' | FromInterfaceOfBase || RequiringInterface
  }

  def 'a rule that declares its own requirements is not compared with its supertypes'() {
    expect:
    RuleDefinition.of(OwnRequirements).requires() == [Requiring]
  }

  def 'a subclass that requires nothing says so with an empty @RequiresRule'() {
    expect:
    RuleDefinition.of(RequiresNothing).requires() == []
  }

  def 'a rule whose supertypes declare no requirements requires nothing'() {
    expect:
    RuleDefinition.of(Plain).requires() == []
  }

  def 'a rule cannot require the same rule twice'() {
    when:
    RuleDefinition.of(RequiringTwice)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "${RequiringTwice.name} requires ${NameUnique.name} twice"
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

  def 'a rule id must have the format of rule ids'() {
    when:
    RuleDefinition.of(BadId)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "the id 'Task' of ${BadId.name} is invalid; an id starts with a lowercase letter and continues with lowercase letters, digits, and single dots or hyphens between them, as in 'vehicle.name-unique'"
  }
}
