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

import java.security.Principal
import spock.lang.Specification

class RuleContextSpec extends Specification {

  def amo = { 'amo' } as Principal

  // Closures, so that the factories run in the feature, where mutation testing sees them.
  def 'a context #how has the rule id, no caller and the root locale'() {
    given:
    def context = create()

    expect:
    context.ruleId() == 'task.ready'
    context.caller().empty
    context.locale() == Locale.ROOT

    where:
    how                      | create
    'for a rule id'          | { RuleContext.forRule('task.ready') }
    'for a rule class'       | { RuleContext.forRule(TaskReady) }
    'built for a rule id'    | { RuleContext.builder('task.ready').build() }
    'built for a rule class' | { RuleContext.builder(TaskReady).build() }
  }

  def 'a context built for a #how knows who makes the call and in which locale'() {
    given:
    def context = builder().caller(amo).locale(Locale.forLanguageTag('tr-TR')).build()

    expect:
    context.ruleId() == 'task.ready'
    context.caller().get().is(amo)
    context.locale() == Locale.forLanguageTag('tr-TR')

    where:
    how          | builder
    'rule id'    | { RuleContext.builder('task.ready') }
    'rule class' | { RuleContext.builder(TaskReady) }
  }

  def 'a context for a rule class that is not a valid rule fails: #problem'() {
    when:
    RuleContext.forRule(ruleClass)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == message

    where:
    problem         | ruleClass || message
    'no @Rule'      | NoRule || "${NoRule.typeName} is not annotated with @Rule"
    'an invalid id' | InvalidId || "the id 'Task Ready' of ${InvalidId.typeName} is invalid; an id starts with a lowercase letter and continues with lowercase letters, digits, and single dots or hyphens between them, as in 'vehicle.name-unique'"
  }

  def 'a context rejects a null rule class'() {
    when:
    RuleContext."$factory"((Class) null)

    then:
    def e = thrown(NullPointerException)
    e.message == 'ruleClass'

    where:
    factory << ['forRule', 'builder']
  }

  def 'a builder from a context starts with its rule, caller and locale'() {
    given:
    def context = RuleContext.builder('request.owner-only').caller(amo).locale(Locale.GERMANY).build()

    when:
    def copy = context.toBuilder().build()

    then:
    copy.ruleId() == 'request.owner-only'
    copy.caller().get().is(amo)
    copy.locale() == Locale.GERMANY
  }

  def 'a context derived for another call leaves the original as it is'() {
    given:
    def context = RuleContext.forRule('task.ready')

    when:
    def derived = context.toBuilder().caller(amo).locale(Locale.forLanguageTag('tr-TR')).build()

    then:
    derived.ruleId() == 'task.ready'
    derived.caller().get().is(amo)
    derived.locale() == Locale.forLanguageTag('tr-TR')
    context.caller().empty
    context.locale() == Locale.ROOT
  }

  def 'a context does not change when its builder is used again'() {
    given:
    def builder = RuleContext.builder('request.owner-only')
    def anonymous = builder.build()

    when:
    def known = builder.caller(amo).locale(Locale.GERMANY).build()

    then:
    anonymous.caller().empty
    anonymous.locale() == Locale.ROOT
    known.caller().get().is(amo)
    known.locale() == Locale.GERMANY
  }

  def 'violations from a built context carry its rule id'() {
    expect:
    RuleContext.builder('request.owner-only').caller(amo).build().violation('request.not-owner').build().ruleId() == 'request.owner-only'
  }

  def 'a context rejects a null #name'() {
    when:
    RuleContext.builder('task.ready')."$name"(null)

    then:
    def e = thrown(NullPointerException)
    e.message == name

    where:
    name << ['caller', 'locale']
  }

  def 'a context rejects "#ruleId", which Muster would not register'() {
    when:
    RuleContext."$factory"(ruleId)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "the rule id '$ruleId' is invalid; an id starts with a lowercase letter and continues with lowercase letters, digits, and single dots or hyphens between them, as in 'vehicle.name-unique'"

    where:
    factory << ['forRule', 'builder']
    combined:
    ruleId << ['', ' ', 'Not An Id', 'task..ready']
  }

  def 'a context rejects a null rule id'() {
    when:
    RuleContext."$factory"((String) null)

    then:
    def e = thrown(NullPointerException)
    e.message == 'ruleId'

    where:
    factory << ['forRule', 'builder']
  }

  def 'a context is about no element unless it is the context of one'() {
    expect:
    RuleContext.forRule('task.ready').elementIndex().empty
    RuleContext.builder('task.ready').elements(0, 2).build().elementIndex().empty
  }

  def 'the context of an element keeps the rule, the caller and the locale, and knows the position'() {
    given:
    def element = RuleContext.builder('task.ready').caller(amo).locale(Locale.forLanguageTag('tr-TR')).build().element(4)

    expect:
    element.ruleId() == 'task.ready'
    element.caller().get().is(amo)
    element.locale() == Locale.forLanguageTag('tr-TR')
    element.elementIndex() == OptionalInt.of(4)
  }

  def 'with positions, the index of an input maps to its position in the checked collection'() {
    given:
    def batch = RuleContext.builder('task.ready').elements(1, 3, 7).build()

    expect:
    (0..2).collect { batch.element(it).elementIndex().asInt } == [1, 3, 7]
  }

  def 'an index that is not that of an input is rejected: #index'() {
    when:
    context().element(index)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == message

    where:
    index | context || message
    -1    | {
      RuleContext.forRule('task.ready')
    } || 'the index -1 is negative'
    -1    | {
      RuleContext.builder('task.ready').elements(1, 3).build()
    } || 'the index -1 is not that of one of the 2 inputs'
    2     | {
      RuleContext.builder('task.ready').elements(1, 3).build()
    } || 'the index 2 is not that of one of the 2 inputs'
  }

  def 'the context of an element gives no context of another element'() {
    when:
    RuleContext.forRule('task.ready').element(1).element(0)

    then:
    def e = thrown(IllegalStateException)
    e.message == 'this is the context of the element at [1] already; get the contexts of elements from the context that checkAll receives'
  }

  def 'positions must be ascending and not negative: #positions'() {
    when:
    RuleContext.builder('task.ready').elements(positions as int[])

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "the positions must be ascending and not negative: ${Arrays.toString(positions as int[])}"

    where:
    positions << [[-1], [0, 0], [2, 1], [0, 3, 2]]
  }

  def 'positions are required'() {
    when:
    RuleContext.builder('task.ready').elements(null)

    then:
    def e = thrown(NullPointerException)
    e.message == 'positions'
  }

  def 'positions are copied, and setting them makes a context of an element a context of the collection'() {
    given:
    def positions = [5, 6] as int[]
    def builder = RuleContext.forRule('task.ready').element(9).toBuilder().elements(positions)
    positions[0] = 0

    when:
    def batch = builder.build()

    then:
    batch.elementIndex().empty
    batch.element(0).elementIndex() == OptionalInt.of(5)
  }

  def 'a builder from a context keeps its positions and its element'() {
    expect:
    RuleContext.builder('task.ready').elements(2, 4).build().toBuilder().build().element(1).elementIndex() == OptionalInt.of(4)
    RuleContext.forRule('task.ready').element(3).toBuilder().build().elementIndex() == OptionalInt.of(3)
  }

  def 'a context describes itself for logs'() {
    given:
    def anonymous = RuleContext.forRule('task.ready')
    def known = RuleContext.builder('request.owner-only').caller(amo).locale(Locale.forLanguageTag('tr-TR')).build()

    expect:
    anonymous.toString() == 'RuleContext[ruleId=task.ready, locale=und]'
    known.toString() == 'RuleContext[ruleId=request.owner-only, caller=amo, locale=tr-TR]'
    known.element(2).toString() == 'RuleContext[ruleId=request.owner-only, caller=amo, locale=tr-TR, element=2]'
  }

  @Rule(id = 'task.ready')
  static class TaskReady implements BusinessRule<String> {
    @Override
    RuleResult check(String input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static class NoRule implements BusinessRule<String> {
    @Override
    RuleResult check(String input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  @Rule(id = 'Task Ready')
  static class InvalidId implements BusinessRule<String> {
    @Override
    RuleResult check(String input, RuleContext ctx) {
      RuleResult.passed()
    }
  }
}
