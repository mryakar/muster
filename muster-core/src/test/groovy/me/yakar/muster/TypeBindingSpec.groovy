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

import java.lang.ref.WeakReference
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import me.yakar.muster.rule.RuleTarget

/** Checking rule targets with rules bound by type. */
class TypeBindingSpec extends EngineSpecification {

  def 'rules bound by type check in the mode of the Muster'() {
    when:
    Muster.builder().rules(new NameUnique(), new CodeFormat()).mode(mode).build().check(INVALID)

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.ruleId() == reported

    where:
    mode || reported
    CheckMode.FAIL_FAST || ['vehicle.code-format']
    CheckMode.COLLECT_ALL || [
      'vehicle.code-format',
      'vehicle.name-unique'
    ]
  }

  def 'rules bound by type run in the order of their ids, whatever the order they are registered in'() {
    when:
    Muster.builder().rules(*rules).mode(CheckMode.COLLECT_ALL).build().check(INVALID)

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.ruleId() == [
      'vehicle.code-format',
      'vehicle.name-unique'
    ]

    where:
    rules << [
      [
        new NameUnique(),
        new CodeFormat()
      ],
      [
        new CodeFormat(),
        new NameUnique()
      ]
    ]
  }

  def 'a rule target is checked by every rule whose input type accepts it, and by no other'() {
    given:
    log.level = Level.FINE
    def muster = Muster.builder().rules(new NameUnique(), new CodeFormat(), new VehicleExists(), new VehicleKnown()).mode(CheckMode.COLLECT_ALL).build()

    when:
    muster.check(INVALID)

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.ruleId() == [
      'vehicle.code-format',
      'vehicle.name-unique'
    ]
    logged*.message == [
      "Rules bound to ${CreateVehicle.name}: [vehicle.code-format, vehicle.name-unique]"
    ]

    cleanup:
    log.level = null
  }

  def 'rules bind to the class of the input at run time'() {
    given:
    def muster = Muster.builder().rules(new NameUnique(), new VehicleKnown()).mode(CheckMode.COLLECT_ALL).build()
    VehicleFields create = new CreateVehicle('TR-1', 'taken')
    VehicleFields update = new UpdateVehicle(404L, 'TR-1', 'taken')

    when:
    muster.check(create)

    then:
    def first = thrown(BusinessRuleViolationException)
    first.violations()*.ruleId() == ['vehicle.name-unique']

    when:
    muster.check(update)

    then:
    def second = thrown(BusinessRuleViolationException)
    second.violations()*.ruleId() == [
      'vehicle.known',
      'vehicle.name-unique'
    ]
  }

  def 'a rule whose input type is Object checks every rule target'() {
    when:
    Muster.builder().rules(new ReportsCall()).build().check(new Unbound('anything'))

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.ruleId() == ['call.reported']
  }

  def 'rules bound by type run after the rules they require, and are skipped when those fail'() {
    given:
    def muster = Muster.builder().rules(new VehicleRenamable(), new VehicleKnown()).build()

    when:
    muster.check(new UpdateVehicle(404L, 'TR-1', 'Atlas'))

    then: 'both rules are bound by type, so the chain adds nothing on its own'
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.ruleId() == ['vehicle.known']
    logged.empty

    when:
    muster.check(new UpdateVehicle(1L, 'TR-1', 'Atlas'))

    then:
    def second = thrown(BusinessRuleViolationException)
    second.violations()*.ruleId() == ['vehicle.a-renamable']
    logged.empty
  }

  def 'a disabled rule does not bind by type'() {
    when:
    Muster.builder().rules(new NameUnique(), new CodeFormat()).disable('vehicle.name-unique').build().check(INVALID)

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.ruleId() == ['vehicle.code-format']
  }

  def 'Muster binds the rules for a class once, and logs them, or warns that none binds'() {
    given:
    log.level = Level.FINE
    def muster = Muster.builder().rules(new NameUnique(), new CodeFormat()).build()

    when:
    muster.check(VALID)
    muster.check(VALID)
    muster.check(new Unbound('nothing binds'))
    muster.check(new Unbound('nothing binds'))

    then:
    logged*.level == [Level.FINE, Level.WARNING]
    logged*.message == [
      "Rules bound to ${CreateVehicle.name}: [vehicle.code-format, vehicle.name-unique]",
      "No rule binds to ${Unbound.name}; check that its rules are registered, enabled and accept it"
    ]

    cleanup:
    log.level = null
  }

  def 'threads that check a new class at the same time bind its rules once'() {
    given:
    log.level = Level.FINE
    def muster = Muster.builder().rules(new NameUnique()).build()
    def start = new CountDownLatch(1)
    def pool = Executors.newFixedThreadPool(16)
    def checks = (1..16).collect {
      pool.submit {
        start.await(); muster.check(VALID)
      }
    }

    when:
    start.countDown()
    checks*.get()

    then:
    logged*.message == [
      "Rules bound to ${CreateVehicle.name}: [vehicle.name-unique]"
    ]

    cleanup:
    pool.shutdownNow()
    log.level = null
  }

  def 'threads that check a class being bound and one already bound each get the rules of its class'() {
    given: 'a Muster that has bound a class no rule binds to, but not CreateVehicle'
    log.level = Level.FINE
    def muster = Muster.builder().rules(new NameUnique(), new CodeFormat()).build()
    muster.check(new Unbound('text'))
    def start = new CountDownLatch(1)
    def pool = Executors.newFixedThreadPool(16)
    def runs = (1..16).collect {
      pool.submit({
        start.await()
        def wrong = 0
        1_000.times {
          try {
            muster.check(INVALID)
            wrong++
          } catch (BusinessRuleViolationException e) {
            if (e.violations()*.ruleId() != ['vehicle.code-format']) {
              wrong++
            }
          }
          muster.check(VALID)
          muster.check(new Unbound('text'))
        }
        wrong
      } as Callable<Integer>)
    }

    when:
    start.countDown()
    def mistakes = runs.collect { it.get(1, TimeUnit.MINUTES) }

    then: 'every check saw the rules of its class, and each class was bound once'
    mistakes.every { it == 0 }
    logged*.message.toSorted() == [
      "No rule binds to ${Unbound.name}; check that its rules are registered, enabled and accept it",
      "Rules bound to ${CreateVehicle.name}: [vehicle.code-format, vehicle.name-unique]"
    ]

    cleanup:
    pool.shutdownNow()
    log.level = null
  }

  def 'checking a bound class takes no lock, even while another class in its bucket is being bound'() {
    given: 'three classes in one bucket of the chain map, the first two of them bound'
    def (first, second, third) = sameBucket(3).collect {
      it.getDeclaredConstructor().newInstance() as RuleTarget
    }
    def muster = Muster.builder().rules(new AnyTarget()).build()
    muster.check(first)
    muster.check(second)

    and: 'a log handler that holds up the binding of the third class'
    log.level = Level.FINE
    def binding = new CountDownLatch(1)
    def release = new CountDownLatch(1)
    def holder = new Handler() {
          void publish(LogRecord record) {
            if (record.message.contains(third.class.name)) {
              binding.countDown()
              release.await()
            }
          }

          void flush() {}

          void close() {}
        }
    log.addHandler(holder)
    def pool = Executors.newFixedThreadPool(2)

    when: 'one thread binds the third class while another checks the second'
    def binder = pool.submit { muster.check(third) }
    assert binding.await(1, TimeUnit.MINUTES)
    pool.submit { muster.check(second) }.get(5, TimeUnit.SECONDS)

    then:
    noExceptionThrown()

    cleanup:
    release.countDown()
    binder?.get(1, TimeUnit.MINUTES)
    log.removeHandler(holder)
    pool.shutdownNow()
    log.level = null
  }

  /** Classes whose hashes fall into the same bucket of a map of sixteen, as ConcurrentHashMap spreads them. */
  private static List<Class<?>> sameBucket(int count) {
    def loader = new GroovyClassLoader(TypeBindingSpec.classLoader)
    def buckets = [:].withDefault { [] }
    for (def i = 0; ; i++) {
      def type = loader.parseClass("class Probe$i implements ${RuleTarget.name} {}")
      def hash = type.hashCode()
      def bucket = buckets[(hash ^ (hash >>> 16)) & 0x7fffffff & 15]
      bucket << type
      if (bucket.size() == count) {
        return bucket
      }
    }
  }

  @Rule(id = 'target.any')
  static class AnyTarget implements BusinessRule<RuleTarget> {
    RuleResult check(RuleTarget input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  def 'a Muster that is no longer used does not keep its rules alive'() {
    given:
    def rule = checkedByDroppedMuster()

    when:
    for (def i = 0; i < 100 && rule.get() != null; i++) {
      System.gc()
      Thread.sleep(10)
    }

    then:
    rule.get() == null
  }

  private static WeakReference<NameUnique> checkedByDroppedMuster() {
    def rule = new NameUnique()
    Muster.builder().rules(rule).build().check(VALID)
    new WeakReference<>(rule)
  }

  def 'a null rule target is rejected'() {
    when:
    Muster.builder().build().check(null)

    then:
    def e = thrown(NullPointerException)
    e.message == 'input'
  }
}
