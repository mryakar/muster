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
import java.security.Principal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.function.Supplier
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.RequiresRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import me.yakar.muster.rule.RuleTarget
import me.yakar.muster.spi.RuleFactory
import spock.lang.Specification

class MusterSpec extends Specification {

  static interface VehicleFields extends RuleTarget {
    String code()

    String name()
  }

  static interface Identified {
    Long id()
  }

  static record CreateVehicle(String code, String name) implements VehicleFields {}

  static record UpdateVehicle(Long id, String code, String name) implements VehicleFields, Identified {}

  static record Unbound(String text) implements RuleTarget {}

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

  /** Throws without wrapping, as a factory in another JVM language may, checked exceptions included. */
  static class ThrowingFactory implements RuleFactory {
    final Exception failure

    ThrowingFactory(Exception failure) {
      this.failure = failure
    }

    BusinessRule<?> create(Class<? extends BusinessRule<?>> ruleClass) {
      throw failure
    }
  }

  /** A rule about a task that notes when it runs, and fails for one task number. */
  static abstract class TaskRule implements BusinessRule<Long> {
    final List<String> ran
    final long failsFor

    TaskRule(List<String> ran, long failsFor) {
      this.ran = ran
      this.failsFor = failsFor
    }

    RuleResult check(Long input, RuleContext ctx) {
      ran << ctx.ruleId()
      input == failsFor ? RuleResult.failed(ctx.violation().build()) : RuleResult.passed()
    }
  }

  @Rule(id = 'task.exists')
  static class TaskExists extends TaskRule {
    TaskExists(List<String> ran) {
      super(ran, 404L)
    }
  }

  @Rule(id = 'task.assignable')
  @RequiresRule(TaskExists)
  static class TaskAssignable extends TaskRule {
    TaskAssignable(List<String> ran) {
      super(ran, 409L)
    }
  }

  @Rule(id = 'task.started')
  @RequiresRule(TaskAssignable)
  static class TaskStarted extends TaskRule {
    TaskStarted(List<String> ran) {
      super(ran, 410L)
    }
  }

  @Rule(id = 'task.imported')
  static class TaskImported extends TaskRule {
    TaskImported(List<String> ran) {
      super(ran, -1L)
    }

    boolean appliesTo(RuleContext ctx) {
      false
    }
  }

  @Rule(id = 'task.from-import')
  @RequiresRule(TaskImported)
  static class TaskFromImport extends TaskRule {
    TaskFromImport(List<String> ran) {
      super(ran, 1L)
    }
  }

  @Rule(id = 'task.locked')
  static class TaskLocked extends TaskRule {
    TaskLocked(List<String> ran) {
      super(ran, 423L)
    }
  }

  @Rule(id = 'task.closable')
  @RequiresRule([TaskExists, TaskLocked])
  static class TaskClosable extends TaskRule {
    TaskClosable(List<String> ran) {
      super(ran, -1L)
    }
  }

  @Rule(id = 'task.reopened')
  @RequiresRule(TaskExists)
  static class TaskReopened extends TaskRule {
    TaskReopened(List<String> ran) {
      super(ran, -1L)
    }
  }

  @Rule(id = 'vehicle.known')
  static class VehicleKnown implements BusinessRule<Identified> {
    RuleResult check(Identified input, RuleContext ctx) {
      input.id() == 404L ? RuleResult.failed(ctx.violation().build()) : RuleResult.passed()
    }
  }

  @Rule(id = 'vehicle.a-renamable')
  @RequiresRule(VehicleKnown)
  static class VehicleRenamable implements BusinessRule<UpdateVehicle> {
    RuleResult check(UpdateVehicle input, RuleContext ctx) {
      RuleResult.failed(ctx.violation().build())
    }
  }

  @Rule(id = 'task.audited')
  static class TaskAudited extends TaskRule {
    TaskAudited(List<String> ran) {
      super(ran, 500L)
    }
  }

  @Rule(id = 'task.billed')
  static class TaskBilled extends TaskRule {
    TaskBilled(List<String> ran) {
      super(ran, 500L)
    }
  }

  @Rule(id = 'plan.capacity-sufficient')
  static class CapacitySufficient implements BusinessRule<Long> {
    RuleResult check(Long input, RuleContext ctx) {
      RuleResult.failed(ctx.violation('plan.passenger-capacity').build(), ctx.violation('plan.parcel-capacity').build())
    }
  }

  @Rule(id = 'vehicle.impostor')
  static class Impostor implements BusinessRule<Object> {
    RuleResult check(Object input, RuleContext ctx) {
      RuleResult.failed(ctx.violation().build(), RuleContext.forRule('vehicle.name-unique').violation().build())
    }
  }

  def amo = { 'amo' } as Principal

  List<String> ran = []
  List<LogRecord> logged = []
  Logger log = Logger.getLogger('me.yakar.muster')
  Handler handler = new Handler() {
    void publish(LogRecord record) {
      logged << record
    }

    void flush() {}

    void close() {}
  }

  def setup() {
    log.addHandler(handler)
  }

  def cleanup() {
    log.removeHandler(handler)
  }

  /** In FAIL_FAST mode a failed rule ends the check, so only COLLECT_ALL shows which rules are skipped. */
  Muster tasks() {
    Muster.builder()
        .rules(new TaskExists(ran), new TaskAssignable(ran), new TaskStarted(ran), new TaskImported(ran), new TaskFromImport(ran))
        .mode(CheckMode.COLLECT_ALL)
        .build()
  }

  def 'a rule runs after the rules it requires, whatever their ids'() {
    when:
    tasks().chain(Long, TaskAssignable, TaskExists).check(1L)

    then:
    ran == [
      'task.exists',
      'task.assignable'
    ]
  }

  def 'when task #task fails #failing, the rules that require it are skipped'() {
    when:
    tasks().chain(Long, TaskStarted, TaskAssignable, TaskExists).check(task)

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.ruleId() == [failing]
    ran == expectedRan

    where:
    task | failing || expectedRan
    404L | 'task.exists' || ['task.exists']
    409L | 'task.assignable' || [
      'task.exists',
      'task.assignable'
    ]
    410L | 'task.started' || [
      'task.exists',
      'task.assignable',
      'task.started'
    ]
  }

  def 'a rule is skipped only when one of its own required rules did not pass'() {
    given: 'the order is exists, locked, closable, reopened'
    def chain = Muster.builder()
        .rules(new TaskExists(ran), new TaskLocked(ran), new TaskClosable(ran), new TaskReopened(ran))
        .mode(CheckMode.COLLECT_ALL)
        .build()
        .chain(Long, TaskClosable, TaskReopened)

    when:
    chain.check(423L)

    then: 'closable needs locked, which failed; reopened needs only exists'
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.ruleId() == ['task.locked']
    ran == [
      'task.exists',
      'task.locked',
      'task.reopened'
    ]
  }

  def 'a required rule that several listed rules share is added and logged once, and gates them all'() {
    given:
    def chain = Muster.builder().rules(new TaskExists(ran), new TaskAssignable(ran), new TaskReopened(ran))
        .mode(CheckMode.COLLECT_ALL).build()
        .chain(Long, TaskAssignable, TaskReopened)

    expect:
    logged*.message == [
      "The chain for java.lang.Long includes rule 'task.exists' because 'task.assignable' requires it"
    ]

    when:
    chain.check(404L)

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.ruleId() == ['task.exists']
    ran == ['task.exists']
  }

  def 'a rule whose required rule does not apply is skipped'() {
    when:
    tasks().chain(Long, TaskFromImport, TaskImported).check(1L)

    then:
    noExceptionThrown()
    ran.empty
  }

  def 'a chain includes the rules that its rules require, and logs each one'() {
    when:
    tasks().chain(Long, TaskStarted).check(1L)

    then:
    ran == [
      'task.exists',
      'task.assignable',
      'task.started'
    ]
    logged*.level == [Level.INFO, Level.INFO]
    logged*.message == [
      "The chain for java.lang.Long includes rule 'task.assignable' because 'task.started' requires it",
      "The chain for java.lang.Long includes rule 'task.exists' because 'task.assignable' requires it"
    ]
  }

  def 'a chain that lists the required rules logs nothing'() {
    when:
    tasks().chain(Long, TaskStarted, TaskAssignable, TaskExists)

    then:
    logged.empty
  }

  def 'a disabled rule runs in no chain, even when the chain lists it'() {
    when:
    Muster.builder().rules(new NameUnique(), new CodeFormat()).disable('vehicle.name-unique').build()
        .chain(CreateVehicle, NameUnique, CodeFormat).check(INVALID)

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.ruleId() == ['vehicle.code-format']
  }

  def 'Muster logs every disabled rule when it is built'() {
    when:
    Muster.builder().rules(new NameUnique(), new CodeFormat()).disable('vehicle.name-unique', 'vehicle.code-format').build()

    then:
    logged*.level == [Level.WARNING, Level.WARNING]
    logged*.message == [
      "Rule 'vehicle.name-unique' is disabled",
      "Rule 'vehicle.code-format' is disabled"
    ]
  }

  def 'a disabled rule is still created, so that switching it on cannot reveal a setup error'() {
    given:
    def created = []
    RuleFactory factory = {
      created << it; it.getDeclaredConstructor().newInstance()
    }

    when:
    Muster.builder().rules(factory, NameUnique, CodeFormat).disable('vehicle.name-unique').build()

    then:
    created == [NameUnique, CodeFormat]
  }

  def 'a rule may be disabled while the rules it requires stay enabled'() {
    when:
    Muster.builder().rules(new TaskExists(ran), new TaskAssignable(ran), new TaskStarted(ran)).disable('task.started').build()
        .chain(Long, TaskStarted, TaskAssignable, TaskExists).check(410L)

    then:
    noExceptionThrown()
    ran == [
      'task.exists',
      'task.assignable'
    ]
  }

  def 'a disabled rule adds none of the rules it requires to a chain'() {
    when:
    Muster.builder().rules(new TaskExists(ran), new TaskAssignable(ran)).disable('task.exists', 'task.assignable').build()
        .chain(Long, TaskAssignable).check(404L)

    then:
    noExceptionThrown()
    ran.empty
    logged*.level == [Level.WARNING, Level.WARNING]
  }

  def 'a disabled rule listed in a chain still has to accept its input'() {
    when:
    Muster.builder().rules(new NameUnique(), new VehicleExists()).disable('vehicle.exists').build()
        .chain(CreateVehicle, NameUnique, VehicleExists)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "${VehicleExists.name} checks java.lang.Long, which ${CreateVehicle.name} is not"
  }

  def 'a disabled rule still has to have its required rules registered'() {
    when:
    Muster.builder().rules(new TaskAssignable(ran)).disable('task.assignable').build()

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "${TaskAssignable.name} requires ${TaskExists.name}, which is not registered"
  }

  def 'a rule that requires a disabled rule must be disabled as well'() {
    when:
    Muster.builder().rules(new TaskExists(ran), new TaskAssignable(ran)).disable('task.exists').build()

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "'task.assignable' requires 'task.exists', which is disabled; disable 'task.assignable' as well, or enable 'task.exists'"
  }

  def 'disabling an unknown id is rejected, so that a typo in configuration is not ignored'() {
    when:
    Muster.builder().rules(new NameUnique()).disable('vehicle.nme-unique').build()

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "cannot disable 'vehicle.nme-unique': no rule has that id"
  }

  def 'Muster checks the rule requirements when it is built'() {
    when:
    Muster.builder().rules(new TaskAssignable(ran)).build()

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "${TaskAssignable.name} requires ${TaskExists.name}, which is not registered"
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

    expect:
    muster.chain(CreateVehicle, NameUnique, CodeFormat).toString() ==
        "RuleChain[input=${CreateVehicle.name}, mode=FAIL_FAST, rules=[vehicle.code-format, vehicle.name-unique]]"
    muster.chain(long).withMode(CheckMode.COLLECT_ALL).toString() == 'RuleChain[input=java.lang.Long, mode=COLLECT_ALL, rules=[]]'
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

  def 'a factory that fails with #failure stops the build'() {
    given:
    def factory = new ThrowingFactory(failure)

    when:
    Muster.builder().rules(factory, NameUnique).build()

    then:
    def e = thrown(IllegalStateException)
    e.message == "the rule factory could not create ${NameUnique.name}"
    e.cause.is(failure)

    where:
    failure << [
      new IllegalStateException('no such bean'),
      new IOException('disk unavailable')
    ]
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
      { it.locale(null) },
      { it.disable('task.exists', null) },
      { it.mode(null) }
    ]
    message << [
      'rule',
      'factory',
      'rule class',
      'caller',
      'locale',
      'id',
      'mode'
    ]
  }
}
