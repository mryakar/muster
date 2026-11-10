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
import spock.lang.Shared
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

  def 'violations of #what name #target, with the code of the rule or their own'() {
    expect:
    context.violation().target('name').build().target() == Optional.of(target)
    context.violation('task.late').target('name').build().target() == Optional.of(target)

    where:
    what                        | context || target
    'a single check'            | RuleContext.forRule('task.ready') || 'name'
    'a collection'              | RuleContext.builder('task.ready').positions(1, 3).build() || 'name'
    'an element'                | RuleContext.forRule('task.ready').element(2) || '[2].name'
    'an element with positions' | RuleContext.builder('task.ready').positions(1, 3).build().element(1) || '[3].name'
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
    RuleContext.forRule('task.ready').elementPosition().empty
    RuleContext.builder('task.ready').positions(0, 2).build().elementPosition().empty
  }

  def 'the context of an element keeps the rule, the caller and the locale, and knows the position'() {
    given:
    def element = RuleContext.builder('task.ready').caller(amo).locale(Locale.forLanguageTag('tr-TR')).build().element(4)

    expect:
    element.ruleId() == 'task.ready'
    element.caller().get().is(amo)
    element.locale() == Locale.forLanguageTag('tr-TR')
    element.elementPosition() == OptionalInt.of(4)
  }

  def 'with positions, the index of an input maps to its position in the checked collection'() {
    given:
    def batch = RuleContext.builder('task.ready').positions(1, 3, 7).build()

    expect:
    (0..2).collect { batch.element(it).elementPosition().asInt } == [1, 3, 7]
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
      RuleContext.builder('task.ready').positions(1, 3).build()
    } || 'the index -1 is not that of one of the 2 inputs'
    2     | {
      RuleContext.builder('task.ready').positions(1, 3).build()
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
    RuleContext.builder('task.ready').positions(positions as int[])

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "the positions must be ascending and not negative: ${Arrays.toString(positions as int[])}"

    where:
    positions << [[-1], [0, 0], [2, 1], [0, 3, 2]]
  }

  def 'positions are required'() {
    when:
    RuleContext.builder('task.ready').positions(null)

    then:
    def e = thrown(NullPointerException)
    e.message == 'positions'
  }

  def 'positions are copied, and setting them makes a context of an element a context of the collection'() {
    given:
    def positions = [5, 6] as int[]
    def builder = RuleContext.forRule('task.ready').element(9).toBuilder().positions(positions)
    positions[0] = 0

    when:
    def batch = builder.build()

    then:
    batch.elementPosition().empty
    batch.element(0).elementPosition() == OptionalInt.of(5)
  }

  def 'a builder from a context keeps its positions and its element'() {
    expect:
    RuleContext.builder('task.ready').positions(2, 4).build().toBuilder().build().element(1).elementPosition() == OptionalInt.of(4)
    RuleContext.forRule('task.ready').element(3).toBuilder().build().elementPosition() == OptionalInt.of(3)
  }

  def 'the context of a rule in a single check is the plain class, which carries no positions'() {
    expect: 'each rule of a check gets its own context, so it stays as small as it can be'
    RuleContext.forRule('task.ready').toBuilder().caller(amo).locale(Locale.GERMANY).build().getClass() == RuleContext
    RuleContext.forRule('task.ready').element(3).getClass() != RuleContext
    RuleContext.builder('task.ready').positions(2, 4).build().getClass() != RuleContext
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

  @Rule(id = 'vehicle.exists')
  static class VehicleExists implements ValueRule<Long, StringBuilder> {
    @Override
    RuleResult check(Long input, RuleContext ctx) {
      RuleResult.passed(new StringBuilder())
    }
  }

  @Rule(id = 'driver.exists')
  static class DriverExists implements ValueRule<Long, String> {
    @Override
    RuleResult check(Long input, RuleContext ctx) {
      RuleResult.passed('driver')
    }
  }

  @Shared
  def atlas = new StringBuilder('Atlas')
  @Shared
  def zeus = new StringBuilder('Zeus')

  def 'a test gives the value a required rule left, and the context reads it'() {
    when:
    def ctx = RuleContext.builder('vehicle.owned').value(VehicleExists, atlas).value(DriverExists, 'ece').build()

    then:
    ctx.valueOf(VehicleExists).is(atlas)
    ctx.valueOf(DriverExists) == 'ece'
    ctx.toBuilder().locale(Locale.GERMANY).build().valueOf(VehicleExists).is(atlas)
  }

  def 'a builder from a context with given values keeps them, and more can be given'() {
    given:
    def ctx = RuleContext.builder('vehicle.owned').value(VehicleExists, atlas).values(DriverExists, ['ece', 'can']).positions(1, 3).build()

    when:
    def more = ctx.toBuilder().value(DriverExists, 'amo').build()
    def same = ctx.toBuilder().locale(Locale.GERMANY).build()

    then:
    more.valueOf(VehicleExists).is(atlas)
    more.valueOf(DriverExists) == 'amo'
    same.valueOf(VehicleExists).is(atlas)
    same.element(1).valueOf(DriverExists) == 'can'
    same.element(1).elementPosition().asInt == 3
  }

  def 'a value given again replaces the one before'() {
    expect:
    RuleContext.builder('vehicle.owned').value(VehicleExists, atlas).value(VehicleExists, zeus).build().valueOf(VehicleExists).is(zeus)
  }

  def 'a context reads no value that was not given: #how'() {
    when:
    create().valueOf(VehicleExists)

    then:
    def e = thrown(IllegalStateException)
    e.message == message

    where:
    how            | create || message
    'none given'   | {
      RuleContext.forRule('vehicle.owned')
    } || "rule 'vehicle.owned' has no value of rule 'vehicle.exists': add VehicleExists to its @RequiresRule, or to that of a rule it requires; in a test, give the value with RuleContext.Builder.value"
    'another given' | {
      RuleContext.builder('vehicle.owned').value(DriverExists, 'ece').build()
    } || "no value of rule 'vehicle.exists' was given to this context; give it with RuleContext.Builder.value, or with values for each element"
  }

  def 'a test gives a value only of a value rule, and of its value type: #what'() {
    when:
    give()

    then:
    def e = thrown(IllegalArgumentException)
    e.message == message

    where:
    what            | give || message
    'not a value rule' | {
      RuleContext.builder('vehicle.owned').value((Class) TaskReady, 'x')
    } || "${TaskReady.typeName} is not a value rule; it leaves no value"
    'another type'  | {
      RuleContext.builder('vehicle.owned').value((Class) VehicleExists, 'Atlas')
    } || "the value of ${VehicleExists.typeName} is a java.lang.String, not a java.lang.StringBuilder"
    'another type in a list' | {
      RuleContext.builder('vehicle.owned').values((Class) VehicleExists, [atlas, 'x'])
    } || "the value of ${VehicleExists.typeName} is a java.lang.String, not a java.lang.StringBuilder"
  }

  def 'a given value rejects null'() {
    when:
    give()

    then:
    thrown(NullPointerException)

    where:
    give << [
      { RuleContext.builder('vehicle.owned').value(null, atlas) },
      { RuleContext.builder('vehicle.owned').value(VehicleExists, null) },
      { RuleContext.builder('vehicle.owned').values(VehicleExists, null) },
      { RuleContext.builder('vehicle.owned').values(VehicleExists, [atlas, null]) },
      { RuleContext.forRule('vehicle.owned').valueOf(null) }
    ]
  }

  def 'a test gives the value of each input, and the context of each element reads its own'() {
    when:
    def ctx = RuleContext.builder('vehicle.owned').values(VehicleExists, [atlas, zeus]).build()

    then:
    ctx.element(0).valueOf(VehicleExists).is(atlas)
    ctx.element(1).valueOf(VehicleExists).is(zeus)
  }

  def 'with positions, the value of each input is that of its element'() {
    when:
    def ctx = RuleContext.builder('vehicle.owned').values(VehicleExists, [atlas, zeus]).positions(2, 5).build()

    then:
    ctx.element(0).valueOf(VehicleExists).is(atlas)
    ctx.element(0).elementPosition().asInt == 2
    ctx.element(1).valueOf(VehicleExists).is(zeus)
  }

  def 'with no positions and no inputs, no element has a value'() {
    when:
    RuleContext.builder('vehicle.owned').values(VehicleExists, []).positions().build()

    then:
    notThrown(Exception)
  }

  def 'the values of each input are as many as the positions'() {
    when:
    RuleContext.builder('vehicle.owned').values(VehicleExists, [atlas]).positions(2, 5).build()

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "the values of ${VehicleExists.typeName} are 1, the inputs 2"
  }

  def 'a value of each element is read from the context of an element only, and a value for the whole from no element: #how'() {
    when:
    read()

    then:
    def e = thrown(IllegalStateException)
    e.message == message

    where:
    how                    | read || message
    'each, from the whole' | {
      RuleContext.builder('vehicle.owned').values(VehicleExists, [atlas]).build().valueOf(VehicleExists)
    } || "rule 'vehicle.exists' leaves a value for each element; read it from the context of an element, as in ctx.element(i).valueOf(VehicleExists.class)"
    'each, past the inputs' | {
      RuleContext.builder('vehicle.owned').values(VehicleExists, [atlas]).build().element(1).valueOf(VehicleExists)
    } || "no value of rule 'vehicle.exists' was given for the element at [1]; give one for each input with RuleContext.Builder.values"
    'whole, from an element' | {
      RuleContext.builder('vehicle.owned').value(VehicleExists, atlas).build().element(0).valueOf(VehicleExists)
    } || "the value of rule 'vehicle.exists' was given for the whole context, not for each element; give one for each input with RuleContext.Builder.values"
  }

  def 'a context with values is not the plain class'() {
    expect:
    RuleContext.builder('vehicle.owned').value(VehicleExists, atlas).build().getClass() != RuleContext
  }
}
