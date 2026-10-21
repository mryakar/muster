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
package me.yakar.muster.rule

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Inherited
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import spock.lang.Specification

class BusinessRuleSpec extends Specification {

  @Rule(id = 'vehicle.name-unique')
  static class VehicleNameUnique implements BusinessRule<String> {

    @Override
    RuleResult check(String input, RuleContext ctx) {
      input == 'taken' ? RuleResult.failed(ctx.violation().build()) : RuleResult.passed()
    }
  }

  static class VehicleNameUniqueForFleet extends VehicleNameUnique {}

  def 'a rule applies to every call unless it says otherwise'() {
    expect:
    new VehicleNameUnique().appliesTo(RuleContext.forRule('vehicle.name-unique'))
  }

  def 'a rule reports violations through its result'() {
    given:
    def ctx = RuleContext.forRule('vehicle.name-unique')

    expect:
    new VehicleNameUnique().check('free', ctx) == RuleResult.passed()
    new VehicleNameUnique().check('taken', ctx) == RuleResult.failed(ctx.violation().build())
  }

  def 'the id of a rule can be read from its class at runtime'() {
    expect:
    VehicleNameUnique.getAnnotation(Rule).id() == 'vehicle.name-unique'
  }

  def 'a subclass of a rule does not inherit its id'() {
    expect:
    VehicleNameUniqueForFleet.getAnnotation(Rule) == null
  }

  def '@#annotation.simpleName is documented, kept at runtime, only allowed on types and not inherited'() {
    expect:
    annotation.isAnnotationPresent(Documented)
    !annotation.isAnnotationPresent(Inherited)
    annotation.getAnnotation(Retention).value() == RetentionPolicy.RUNTIME
    annotation.getAnnotation(Target).value() == [ElementType.TYPE] as ElementType[]

    where:
    annotation << [Rule, RequiresRule]
  }
}
