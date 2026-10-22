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
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import me.yakar.muster.rule.RuleContext

/** Checking an input with a chain: order, skipping, modes, the call context and failures. */
class RuleChainSpec extends EngineSpecification {

  def 'a disabled rule runs in no chain, even when the chain lists it'() {
    when:
    Muster.builder().rules(new NameUnique(), new CodeFormat()).disable('vehicle.name-unique').build()
        .chain(CreateVehicle, NameUnique, CodeFormat).check(INVALID)

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.ruleId() == ['vehicle.code-format']
  }

  def 'a disabled rule listed in a chain still has to accept its input'() {
    when:
    Muster.builder().rules(new NameUnique(), new VehicleExists()).disable('vehicle.exists').build()
        .chain(CreateVehicle, NameUnique, VehicleExists)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "${VehicleExists.name} checks java.lang.Long, which ${CreateVehicle.name} is not"
  }

  def 'an input that violates no rule passes'() {
    given:
    def chain = Muster.builder().rules(new NameUnique(), new CodeFormat()).build()
        .chain(CreateVehicle, NameUnique, CodeFormat)

    when:
    chain.check(VALID)

    then:
    noExceptionThrown()
  }

  def 'in COLLECT_ALL mode, all violations are collected, in the order of the rule ids'() {
    given:
    def muster = Muster.builder().rules(new NameUnique(), new CodeFormat()).mode(CheckMode.COLLECT_ALL).build()

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

  def 'Muster checks in FAIL_FAST mode unless told otherwise'() {
    given:
    def muster = Muster.builder().rules(new TaskAudited(ran), new TaskBilled(ran)).build()
    def chain = muster.chain(Long, TaskBilled, TaskAudited)

    expect:
    chain.mode() == CheckMode.FAIL_FAST

    when:
    chain.check(500L)

    then: 'the check stops at the first violated rule'
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.ruleId() == ['task.audited']
    ran == ['task.audited']
  }

  def 'in COLLECT_ALL mode, every rule runs'() {
    when:
    Muster.builder().rules(new TaskAudited(ran), new TaskBilled(ran)).mode(CheckMode.COLLECT_ALL).build()
        .chain(Long, TaskBilled, TaskAudited).check(500L)

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.ruleId() == ['task.audited', 'task.billed']
    ran == ['task.audited', 'task.billed']
  }

  def 'in FAIL_FAST mode, all violations of the first violated rule are reported'() {
    when:
    Muster.builder().rules(new CapacitySufficient(), new TaskAudited(ran)).build()
        .chain(Long, CapacitySufficient, TaskAudited).check(500L)

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.code() == [
      'plan.passenger-capacity',
      'plan.parcel-capacity'
    ]
    ran.empty
  }

  def 'in FAIL_FAST mode, skipped rules do not stop the check'() {
    when: 'the order is imported, from-import, locked; the first two are skipped'
    Muster.builder().rules(new TaskImported(ran), new TaskFromImport(ran), new TaskLocked(ran)).build()
        .chain(Long, TaskFromImport, TaskLocked).check(423L)

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.ruleId() == ['task.locked']
    ran == ['task.locked']
  }

  def 'a chain checks in its own mode, whatever the mode of the Muster: #global, then #own'() {
    given:
    def chain = Muster.builder().rules(new TaskAudited(ran), new TaskBilled(ran)).mode(global).build()
        .chain(Long, TaskBilled, TaskAudited)

    when:
    def overridden = chain.withMode(own)

    then: 'the original chain keeps the mode of the Muster'
    chain.mode() == global
    overridden.mode() == own

    when:
    overridden.check(500L)

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.ruleId() == reported

    where:
    global                | own || reported
    CheckMode.COLLECT_ALL | CheckMode.FAIL_FAST || ['task.audited']
    CheckMode.FAIL_FAST   | CheckMode.COLLECT_ALL || ['task.audited', 'task.billed']
  }

  def 'a chain rejects a null mode'() {
    when:
    Muster.builder().build().chain(Object).withMode(null)

    then:
    def e = thrown(NullPointerException)
    e.message == 'mode'
  }

  def 'a rule that does not apply to the call is skipped, and the rules after it still run'() {
    when:
    Muster.builder().rules(new ImportOnly(), new NameUnique()).build()
        .chain(CreateVehicle, ImportOnly, NameUnique).check(INVALID)

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.ruleId() == ['vehicle.name-unique']
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

  def 'checks of one chain on many threads each see their own caller and locale'() {
    given: 'a caller and a locale per thread'
    def callers = new ThreadLocal<Principal>()
    def locales = new ThreadLocal<Locale>()
    def chain = Muster.builder()
        .rules(new ReportsCall())
        .caller { callers.get() }
        .locale { locales.get() }
        .build()
        .chain(Object, ReportsCall)
    def threads = 16
    def start = new CountDownLatch(1)
    def pool = Executors.newFixedThreadPool(threads)
    def runs = (0..<threads).collect { n ->
      pool.submit({
        def name = "caller-$n".toString()
        callers.set({ name } as Principal)
        locales.set(Locale.forLanguageTag("tr-${100 + n}"))
        start.await()
        def seen = [] as Set
        2_000.times {
          try {
            chain.check('input')
          } catch (BusinessRuleViolationException e) {
            seen << e.violations()[0].clientArgs()
          }
        }
        seen
      } as Callable<Set>)
    }

    when:
    start.countDown()
    def calls = runs.collect { it.get(1, TimeUnit.MINUTES) }

    then: 'no thread saw the call of another'
    (0..<threads).every { n ->
      calls[n] == [
        [caller: "caller-$n".toString(), locale: "tr-${100 + n}".toString()]
      ] as Set
    }

    cleanup:
    pool.shutdownNow()
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

  def 'a violation reported under another rule\'s id is rejected'() {
    when:
    Muster.builder().rules(new Impostor()).build().chain(Object, Impostor).check('input')

    then:
    def e = thrown(IllegalStateException)
    e.message == "rule 'vehicle.impostor' reported a violation of rule 'vehicle.name-unique'; build violations from the RuleContext the rule receives"
  }

  def 'a chain rejects an input of another type, which only unchecked code can pass'() {
    given:
    RuleChain chain = Muster.builder().rules(new NameUnique()).build().chain(CreateVehicle, NameUnique)

    when:
    chain.check('not a vehicle')

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "the chain checks ${CreateVehicle.name}, not java.lang.String"
  }

  def 'a chain describes itself for logs'() {
    given:
    def muster = Muster.builder().rules(new NameUnique(), new CodeFormat()).build()
    def vehicles = muster.chain(CreateVehicle, NameUnique, CodeFormat)
    def nothing = muster.chain(long).withMode(CheckMode.COLLECT_ALL)

    expect:
    vehicles.toString() == "RuleChain[input=${CreateVehicle.name}, mode=FAIL_FAST, rules=[vehicle.code-format, vehicle.name-unique]]"
    nothing.toString() == 'RuleChain[input=java.lang.Long, mode=COLLECT_ALL, rules=[]]'
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
    'an array input'            | String[]      | [VehicleExists] || "${VehicleExists.name} checks java.lang.Long, which java.lang.String[] is not"
  }

  def 'a chain rejects a null #message'() {
    when:
    Muster.builder().rules(new NameUnique()).build().chain(inputType, *ruleClasses)

    then:
    def e = thrown(NullPointerException)
    e.message == message

    where:
    inputType     | ruleClasses || message
    null          | [NameUnique] || 'inputType'
    CreateVehicle | [NameUnique, null] || 'rule class'
  }

  def 'a locale supplier that returns null fails the check'() {
    when:
    Muster.builder().locale { null }.build().chain(Object).check('input')

    then:
    def e = thrown(NullPointerException)
    e.message == 'the locale supplier returned null'
  }
}
