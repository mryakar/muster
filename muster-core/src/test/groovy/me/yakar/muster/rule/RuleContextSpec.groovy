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

  def 'a context knows the rule it belongs to'() {
    expect:
    RuleContext.forRule('task.ready').ruleId() == 'task.ready'
  }

  def 'a context for a rule class takes the id from its @Rule'() {
    expect:
    RuleContext.forRule(TaskReady).ruleId() == 'task.ready'
    RuleContext.forRule(TaskReady).caller().empty
    RuleContext.forRule(TaskReady).locale() == Locale.ROOT
  }

  def 'a builder for a rule class takes the id from its @Rule'() {
    given:
    def context = RuleContext.builder(TaskReady).caller(amo).locale(Locale.forLanguageTag('tr-TR')).build()

    expect:
    context.ruleId() == 'task.ready'
    context.caller().get().is(amo)
    context.locale() == Locale.forLanguageTag('tr-TR')
  }

  def 'a context for a rule class that is not a valid rule fails: #problem'() {
    when:
    RuleContext.forRule(ruleClass)

    then:
    def e = thrown(exception)
    e.message == message

    where:
    problem         | ruleClass || exception                | message
    'no @Rule'      | NoRule || IllegalArgumentException | "${NoRule.typeName} is not annotated with @Rule"
    'an invalid id' | InvalidId || IllegalArgumentException | "the id 'Task Ready' of ${InvalidId.typeName} is invalid; ${me.yakar.muster.internal.rule.RuleIds.FORMAT}"
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

  def 'a context for testing a rule has no caller and the root locale'() {
    given:
    def context = RuleContext.forRule('task.ready')

    expect:
    context.caller().empty
    context.locale() == Locale.ROOT
  }

  def 'a context knows who makes the call and in which locale'() {
    given:
    def context = RuleContext.builder('request.owner-only').caller(amo).locale(Locale.forLanguageTag('tr-TR')).build()

    expect:
    context.ruleId() == 'request.owner-only'
    context.caller().get().is(amo)
    context.locale() == Locale.forLanguageTag('tr-TR')
  }

  def 'a context built without a caller or a locale has the defaults'() {
    given:
    def context = RuleContext.builder('task.ready').build()

    expect:
    context.ruleId() == 'task.ready'
    context.caller().empty
    context.locale() == Locale.ROOT
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

  def 'a context rejects a null caller or locale'() {
    when:
    set(RuleContext.builder('task.ready'))

    then:
    def e = thrown(NullPointerException)
    e.message == message

    where:
    set << [
      { it.caller(null) },
      { it.locale(null) }
    ]
    message << ['caller', 'locale']
  }

  def 'a context rejects "#ruleId", which Muster would not register'() {
    when:
    RuleContext."$factory"(ruleId)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "the rule id '$ruleId' is invalid; an id starts with a lowercase letter and continues with lowercase letters, digits, and single dots or hyphens between them, as in 'vehicle.name-unique'"

    where:
    [factory, ruleId] << [
      ['forRule', 'builder'],
      [
        '',
        ' ',
        'Not An Id',
        'task..ready'
      ]
    ].combinations()
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

  def 'a context describes itself for logs'() {
    given:
    def anonymous = RuleContext.forRule('task.ready')
    def known = RuleContext.builder('request.owner-only').caller(amo).locale(Locale.forLanguageTag('tr-TR')).build()

    expect:
    anonymous.toString() == 'RuleContext[ruleId=task.ready, locale=und]'
    known.toString() == 'RuleContext[ruleId=request.owner-only, caller=amo, locale=tr-TR]'
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
