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

import java.security.Principal
import java.util.function.Supplier
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import me.yakar.muster.spi.RuleFactory
import spock.lang.Specification

class MusterSpec extends Specification {

  static interface VehicleFields {
    String code()

    String name()
  }

  static record CreateVehicle(String code, String name) implements VehicleFields {}

  static final CreateVehicle VALID = new CreateVehicle('TR-1', 'Atlas')
  static final CreateVehicle INVALID = new CreateVehicle('bad', 'taken')

  @Rule(id = 'vehicle.name-unique')
  static class NameUnique implements BusinessRule<VehicleFields> {
    RuleResult check(VehicleFields input, RuleContext ctx) {
      input.name() == 'taken' ? RuleResult.failed(ctx.violation().clientArg('name', input.name()).build()) : RuleResult.passed()
    }
  }

  @Rule(id = 'vehicle.code-format')
  static class CodeFormat implements BusinessRule<VehicleFields> {
    RuleResult check(VehicleFields input, RuleContext ctx) {
      input.code() ==~ /[A-Z]{2}-\d+/ ? RuleResult.passed() : RuleResult.failed(ctx.violation().build())
    }
  }

  @Rule(id = 'vehicle.exists')
  static class VehicleExists implements BusinessRule<Long> {
    RuleResult check(Long input, RuleContext ctx) {
      input == 404L ? RuleResult.failed(ctx.violation().build()) : RuleResult.passed()
    }
  }

  @Rule(id = 'call.reported')
  static class ReportsCall implements BusinessRule<Object> {
    RuleResult check(Object input, RuleContext ctx) {
      RuleResult.failed(ctx.violation()
          .clientArg('caller', ctx.caller().map { it.name }.orElse('none'))
          .clientArg('locale', ctx.locale().toLanguageTag())
          .build())
    }
  }

  @Rule(id = 'import.only')
  static class ImportOnly implements BusinessRule<Object> {
    boolean appliesTo(RuleContext ctx) {
      false
    }

    RuleResult check(Object input, RuleContext ctx) {
      RuleResult.failed(ctx.violation().build())
    }
  }

  @Rule(id = 'rule.returns-null')
  static class ReturnsNull implements BusinessRule<Object> {
    RuleResult check(Object input, RuleContext ctx) {
      null
    }
  }

  @Rule(id = 'rule.broken')
  static class Broken implements BusinessRule<Object> {
    static final RuntimeException FAILURE = new IllegalStateException('database down')

    RuleResult check(Object input, RuleContext ctx) {
      throw FAILURE
    }
  }

  @Rule(id = 'vehicle.name-unique')
  static class NameUniqueAgain implements BusinessRule<VehicleFields> {
    RuleResult check(VehicleFields input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static class Unannotated implements BusinessRule<Object> {
    RuleResult check(Object input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static class ProxiedNameUnique extends NameUnique {}

  def amo = { 'amo' } as Principal

  def 'an input that violates no rule passes'() {
    given:
    def chain = Muster.builder().rules(new NameUnique(), new CodeFormat()).build()
        .chain(CreateVehicle, NameUnique, CodeFormat)

    when:
    chain.check(VALID)

    then:
    noExceptionThrown()
  }

  def 'all violations are collected, in the order of the rule ids'() {
    given:
    def muster = Muster.builder().rules(new NameUnique(), new CodeFormat()).build()

    when:
    muster.chain(CreateVehicle, *ruleClasses).check(INVALID)

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.ruleId() == [
      'vehicle.code-format',
      'vehicle.name-unique'
    ]
    e.violations()[1].clientArgs() == [name: 'taken']

    where:
    ruleClasses << [
      [NameUnique, CodeFormat],
      [CodeFormat, NameUnique]
    ]
  }

  def 'a rule that does not apply to the call is skipped'() {
    when:
    Muster.builder().rules(new ImportOnly()).build().chain(Object, ImportOnly).check('input')

    then:
    noExceptionThrown()
  }

  def 'a chain with no rules passes every input'() {
    when:
    Muster.builder().build().chain(Object).check('input')

    then:
    noExceptionThrown()
  }

  def 'without a caller or a locale, a call is anonymous and in the default locale'() {
    when:
    Muster.builder().rules(new ReportsCall()).build().chain(Object, ReportsCall).check('input')

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()[0].clientArgs() == [caller: 'none', locale: Locale.default.toLanguageTag()]
  }

  def 'the caller and the locale are asked once at the start of every check'() {
    given:
    def callers = [amo, null]
    def locales = [
      Locale.forLanguageTag('tr-TR'),
      Locale.GERMANY
    ]
    def asked = 0
    def chain = Muster.builder()
        .rules(new ReportsCall(), new NameUnique())
        .caller { asked++; callers.removeFirst() }
        .locale { locales.removeFirst() }
        .build()
        .chain(CreateVehicle, ReportsCall, NameUnique)

    when:
    chain.check(INVALID)

    then:
    def first = thrown(BusinessRuleViolationException)
    first.violations()[0].clientArgs() == [caller: 'amo', locale: 'tr-TR']
    asked == 1

    when:
    chain.check(INVALID)

    then:
    def second = thrown(BusinessRuleViolationException)
    second.violations()[0].clientArgs() == [caller: 'none', locale: 'de-DE']
    asked == 2
  }

  def 'a null input is rejected before any rule runs'() {
    when:
    Muster.builder().rules(new Broken()).build().chain(Object, Broken).check(null)

    then:
    def e = thrown(NullPointerException)
    e.message == 'input'
  }

  def 'a rule that returns null is reported by its id'() {
    when:
    Muster.builder().rules(new ReturnsNull()).build().chain(Object, ReturnsNull).check('input')

    then:
    def e = thrown(NullPointerException)
    e.message == "rule 'rule.returns-null' returned null"
  }

  def 'an exception from a rule propagates as it is'() {
    when:
    Muster.builder().rules(new Broken(), new NameUnique()).build().chain(CreateVehicle, Broken, NameUnique).check(INVALID)

    then:
    def e = thrown(IllegalStateException)
    e.is(Broken.FAILURE)
  }

  def 'a primitive input type stands for its wrapper'() {
    given:
    def chain = Muster.builder().rules(new VehicleExists()).build().chain(long, VehicleExists)

    when:
    chain.check(404L)

    then:
    thrown(BusinessRuleViolationException)
  }

  def 'a chain rejects #problem'() {
    given:
    def muster = Muster.builder().rules(new NameUnique(), new VehicleExists()).build()

    when:
    muster.chain(inputType, *ruleClasses)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == message

    where:
    problem                     | inputType     | ruleClasses || message
    'a rule not registered'     | CreateVehicle | [CodeFormat] || "${CodeFormat.name} is not registered"
    'a rule listed twice'       | CreateVehicle | [NameUnique, NameUnique] || "${NameUnique.name} is listed twice"
    'a rule for another input'  | CreateVehicle | [NameUnique, VehicleExists] || "${VehicleExists.name} checks java.lang.Long, which ${CreateVehicle.name} is not"
    'a wider input than a rule' | Object        | [NameUnique] || "${NameUnique.name} checks ${VehicleFields.name}, which java.lang.Object is not"
  }

  def 'a factory creates each rule once, when Muster is built'() {
    given:
    def created = []
    RuleFactory factory = {
      created << it; it.getDeclaredConstructor().newInstance()
    }
    def builder = Muster.builder().rules(factory, NameUnique, CodeFormat)

    expect:
    created.empty

    when:
    def muster = builder.build()
    def chain = muster.chain(CreateVehicle, NameUnique, CodeFormat)
    chain.check(VALID)
    chain.check(VALID)

    then:
    created == [NameUnique, CodeFormat]
  }

  def 'a factory may return a subclass, such as a proxy'() {
    given:
    RuleFactory factory = { new ProxiedNameUnique() }

    when:
    Muster.builder().rules(factory, NameUnique).build().chain(CreateVehicle, NameUnique).check(INVALID)

    then:
    thrown(BusinessRuleViolationException)
  }

  def 'a factory that fails stops the build'() {
    given:
    def failure = new IllegalStateException('no such bean')
    RuleFactory factory = { throw failure }

    when:
    Muster.builder().rules(factory, NameUnique).build()

    then:
    def e = thrown(IllegalStateException)
    e.message == "the rule factory could not create ${NameUnique.name}"
    e.cause.is(failure)
  }

  def 'a factory that returns #returned stops the build'() {
    when:
    Muster.builder().rules(factory as RuleFactory, NameUnique).build()

    then:
    def e = thrown(IllegalStateException)
    e.message == message

    where:
    returned            | factory || message
    'null'              | {
      null
    } || "the rule factory returned null for ${NameUnique.name}"
    'another rule'      | {
      new CodeFormat()
    } || "the rule factory returned a ${CodeFormat.name} for ${NameUnique.name}"
  }

  def 'registering #problem is rejected'() {
    when:
    register(Muster.builder())

    then:
    def e = thrown(IllegalArgumentException)
    e.message == message

    where:
    problem                       | register || message
    'an unannotated rule'         | {
      it.rules(new Unannotated())
    } || "${Unannotated.name} is not annotated with @Rule"
    'a rule twice'                | {
      it.rules(new NameUnique(), new NameUnique())
    } || "${NameUnique.name} is registered twice"
    'a rule class twice'          | {
      it.rules(new NameUnique()).rules({
        null
      } as RuleFactory, NameUnique)
    } || "${NameUnique.name} is registered twice"
    'two rules with the same id'  | {
      it.rules(new NameUnique(), new NameUniqueAgain())
    } || "${NameUnique.name} and ${NameUniqueAgain.name} share the id 'vehicle.name-unique'"
  }

  def 'a builder rejects a null #message'() {
    when:
    configure(Muster.builder())

    then:
    def e = thrown(NullPointerException)
    e.message == message

    where:
    configure << [
      { it.rules((BusinessRule) null) },
      { it.rules((RuleFactory) null, NameUnique) },
      { it.rules({ null } as RuleFactory, (Class) null) },
      { it.caller(null) },
      { it.locale(null) }
    ]
    message << [
      'rule',
      'factory',
      'rule class',
      'caller',
      'locale'
    ]
  }
}
