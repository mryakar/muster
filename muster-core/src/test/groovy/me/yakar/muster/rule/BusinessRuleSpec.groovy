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
      RuleResult.passed()
    }
  }

  /** Fails a name that is taken; returns null for a blank one. */
  @Rule(id = 'vehicle.name-free')
  static class NameFree implements BusinessRule<String> {
    final List<RuleContext> contexts = []

    RuleResult check(String input, RuleContext ctx) {
      contexts << ctx
      if (input.blank) {
        return null
      }
      input == 'taken' ? RuleResult.failed(ctx.violation().target('name').build()) : RuleResult.passed()
    }
  }

  def 'checkAll checks each input in the context of its element, unless overridden'() {
    given:
    def rule = new NameFree()
    def ctx = RuleContext.builder('vehicle.name-free').elements(2, 5, 9).build()

    when:
    def result = rule.checkAll(['taken', 'Atlas', 'taken'], ctx)

    then:
    result instanceof RuleResult.Failed
    (result as RuleResult.Failed).violations()*.target()*.get() == ['[2].name', '[9].name']
    rule.contexts*.elementIndex()*.asInt == [2, 5, 9]
  }

  def 'checkAll passes when every input passes, and when there is none'() {
    expect:
    new NameFree().checkAll(inputs, RuleContext.forRule('vehicle.name-free')) == RuleResult.passed()

    where:
    inputs << [['Atlas', 'Zeus'], []]
  }

  def 'checkAll fails when check returns null, naming the position of the element'() {
    when:
    new NameFree().checkAll(['Atlas', ' '], RuleContext.builder('vehicle.name-free').elements(3, 8).build())

    then:
    def e = thrown(NullPointerException)
    e.message == "rule 'vehicle.name-free' returned null for the element at [8]"
  }

  def 'a rule applies to every call unless it says otherwise'() {
    expect:
    new VehicleNameUnique().appliesTo(RuleContext.forRule('vehicle.name-unique'))
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
