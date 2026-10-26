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

import static me.yakar.muster.Concurrency.concurrently
import static me.yakar.muster.spi.CheckListener.Outcome.ERROR
import static me.yakar.muster.spi.CheckListener.Outcome.FAILED
import static me.yakar.muster.spi.CheckListener.Outcome.PASSED
import static me.yakar.muster.spi.CheckListener.Outcome.SKIPPED

import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import me.yakar.muster.CollectionBindingSpec.BatchLimit
import me.yakar.muster.CollectionBindingSpec.NamesDistinct
import me.yakar.muster.CollectionCheckSpec.NamesFree
import me.yakar.muster.RuleChainSpec.Impostor
import me.yakar.muster.RuleChainSpec.ReturnsNull
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import me.yakar.muster.spi.CheckListener
import me.yakar.muster.spi.CheckListener.Check
import me.yakar.muster.spi.CheckListener.CheckEnd
import me.yakar.muster.spi.CheckListener.Observer
import me.yakar.muster.spi.CheckListener.RuleEnd

/** What a listener is told about a check: the events, their order, outcomes, durations and exceptions. */
class ObservedCheckSpec extends EngineSpecification {

  /** Records every event of every check it observes, as a list of the kind and the value, per check. */
  static class Recording implements CheckListener, Observer {
    final List<Check> checks = new CopyOnWriteArrayList<>()
    final List<List> events = new CopyOnWriteArrayList<>()

    Observer checkStarted(Check check) {
      checks << check
      events << []
      this
    }

    void ruleStarted(String ruleId) {
      events.last() << ['started', ruleId]
    }

    void ruleEnded(RuleEnd end) {
      events.last() << ['ended', end]
    }

    void checkEnded(CheckEnd end) {
      events.last() << ['check', end]
    }

    /** The events of the only check, as the kind, the rule id or outcome, and for an end its outcome. */
    List<List> summary() {
      assert events.size() == 1
      events.first().collect { kind, value ->
        switch (kind) {
          case 'started' -> [kind, value]
          case 'ended' -> [kind, value.ruleId(), value.outcome()]
          default -> [kind, value.outcome()]
        }
      }
    }

    /** The ends of the rules of the only check, by rule id. */
    Map<String, RuleEnd> ruleEnds() {
      events.first().findAll { it[0] == 'ended' }.collectEntries { [(it[1].ruleId()): it[1]] }
    }

    CheckEnd checkEnd() {
      events.first().last()[1]
    }
  }

  @Rule(id = 'rule.throwing')
  static class Throwing implements BusinessRule<Object> {
    final RuntimeException failure

    Throwing(RuntimeException failure) {
      this.failure = failure
    }

    RuleResult check(Object input, RuleContext ctx) {
      throw failure
    }
  }

  /** Fails whatever it is given, with the id of the vehicle code rule. */
  @Rule(id = 'vehicle.code-format')
  static class CodeFormatOfObject implements BusinessRule<Object> {
    RuleResult check(Object input, RuleContext ctx) {
      RuleResult.failed(ctx.violation().build())
    }
  }

  @Rule(id = 'rule.slow')
  static class Slow implements BusinessRule<Object> {
    RuleResult check(Object input, RuleContext ctx) {
      Thread.sleep(20)
      RuleResult.passed()
    }
  }

  static final CreateVehicle OTHER = new CreateVehicle('TR-2', 'Nova')
  static final CreateVehicle THIRD = new CreateVehicle('TR-3', 'Zed')

  def listener = new Recording()

  def 'a check tells the listener about itself and each rule, in order, and what each took'() {
    given:
    def chain = Muster.builder().rules(new CodeFormat(), new NameUnique()).listener(listener).build()
        .chain(CreateVehicle, CodeFormat, NameUnique)

    when:
    def before = System.nanoTime()
    chain.check(VALID)
    def wallClock = Duration.ofNanos(System.nanoTime() - before)

    then:
    listener.checks == [new Check(CreateVehicle.name, CreateVehicle, CheckMode.FAIL_FAST, false, 1)]
    listener.summary() == [
      ['started', 'vehicle.code-format'],
      ['ended', 'vehicle.code-format', PASSED],
      ['started', 'vehicle.name-unique'],
      ['ended', 'vehicle.name-unique', PASSED],
      ['check', PASSED],
    ]
    listener.ruleEnds().values().every { it.violations().empty && it.error() == null }
    listener.ruleEnds().values().every {
      Duration.ZERO <= it.took() && it.took() <= listener.checkEnd().took()
    }
    listener.checkEnd().violations().empty
    listener.checkEnd().error() == null
    listener.checkEnd().took() >= listener.ruleEnds().values().sum { it.took() }
    listener.checkEnd().took() <= wallClock
  }

  def 'the listener gets how long a rule took, and the check at least as much'() {
    given:
    def chain = Muster.builder().rules(new Slow()).listener(listener).build().chain(Object, Slow)

    when:
    def before = System.nanoTime()
    chain.check('input')
    def wallClock = Duration.ofNanos(System.nanoTime() - before)

    then:
    listener.ruleEnds()['rule.slow'].took() >= Duration.ofMillis(20)
    listener.checkEnd().took() >= listener.ruleEnds()['rule.slow'].took()
    listener.checkEnd().took() <= wallClock
  }

  def 'in fail-fast mode the violated rule and the check end failed, and no later rule starts'() {
    given:
    def chain = Muster.builder().rules(new CodeFormat(), new NameUnique()).listener(listener).build()
        .chain(CreateVehicle, CodeFormat, NameUnique)

    when:
    chain.check(INVALID)

    then:
    def e = thrown(BusinessRuleViolationException)
    listener.summary() == [
      ['started', 'vehicle.code-format'],
      ['ended', 'vehicle.code-format', FAILED],
      ['check', FAILED],
    ]
    listener.ruleEnds()['vehicle.code-format'].violations() == e.violations()
    listener.checkEnd().violations() == e.violations()
    listener.checkEnd().error() == null
  }

  def 'in collect-all mode a rule that does not apply, or whose requirement failed, is skipped, and the check ends with every violation'() {
    given:
    def muster = Muster.builder()
        .rules(new TaskExists(ran), new TaskAssignable(ran), new TaskImported(ran), new TaskFromImport(ran), new TaskLocked(ran))
        .listener(listener)
        .mode(CheckMode.COLLECT_ALL)
        .build()
    def chain = muster.chain(Long, TaskExists, TaskAssignable, TaskImported, TaskFromImport, TaskLocked)

    when:
    chain.check(404L)

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.ruleId() == ['task.exists']
    listener.checks*.mode() == [CheckMode.COLLECT_ALL]
    listener.summary() == [
      ['started', 'task.exists'],
      ['ended', 'task.exists', FAILED],
      ['started', 'task.assignable'],
      ['ended', 'task.assignable', SKIPPED],
      ['started', 'task.imported'],
      ['ended', 'task.imported', SKIPPED],
      ['started', 'task.from-import'],
      ['ended', 'task.from-import', SKIPPED],
      ['started', 'task.locked'],
      ['ended', 'task.locked', PASSED],
      ['check', FAILED],
    ]
    listener.ruleEnds()['task.assignable'].violations().empty
    listener.checkEnd().violations() == e.violations()
  }

  def 'a rule that #what ends with an error, and so does the check, with the exception that propagates'() {
    given:
    def chain = Muster.builder().rules(rule).listener(listener).build().chain(Object, rule.getClass())

    when:
    chain.check('input')

    then:
    def e = thrown(Throwable)
    e.getClass() == exception
    e.suppressed.length == 0
    listener.summary() == [['started', id], ['ended', id, ERROR], ['check', ERROR]]
    listener.ruleEnds()[id].error().is(e)
    listener.ruleEnds()[id].violations().empty
    listener.checkEnd().error().is(e)
    listener.checkEnd().violations().empty

    where:
    what                                   | rule             | id                   | exception
    'throws'                               | new Throwing(new IllegalStateException()) | 'rule.throwing' | IllegalStateException
    'returns null'                         | new ReturnsNull() | 'rule.returns-null' | NullPointerException
    'reports a violation of another rule'  | new Impostor()   | 'vehicle.impostor'   | IllegalStateException
  }

  def 'a rule that throws a rejection, as one that checks another input may, ends with an error, and the check rejected'() {
    given:
    def rejection = new BusinessRuleViolationException([RuleContext.forRule('other.rule').violation().build()])
    def chain = Muster.builder().rules(new Throwing(rejection)).listener(listener).build().chain(Object, Throwing)

    when:
    chain.check('input')

    then:
    def e = thrown(BusinessRuleViolationException)
    e.is(rejection)
    listener.summary() == [['started', 'rule.throwing'], ['ended', 'rule.throwing', ERROR], ['check', FAILED]]
    listener.ruleEnds()['rule.throwing'].error().is(rejection)
    listener.checkEnd().violations() == rejection.violations()
    listener.checkEnd().error() == null
  }

  def 'a supplier that fails ends the check with an error before any rule'() {
    given:
    def failure = new IllegalStateException('no caller')
    def chain = Muster.builder().rules(new CodeFormat()).caller {
      throw failure
    }.listener(listener).build()
    .chain(CreateVehicle, CodeFormat)

    when:
    chain.check(VALID)

    then:
    def e = thrown(IllegalStateException)
    e.is(failure)
    listener.summary() == [['check', ERROR]]
    listener.checkEnd().error().is(failure)
  }

  def 'a listener that returns no observer is told nothing more, and the check runs as it would'() {
    given:
    def started = []
    def chain = Muster.builder().rules(new CodeFormat()).listener({ started << it; null }).build()
    .chain(CreateVehicle, CodeFormat)

    when:
    chain.check(INVALID)

    then:
    thrown(BusinessRuleViolationException)
    started*.inputType() == [CreateVehicle]
  }

  def 'without a listener the check runs as it would'() {
    when:
    Muster.builder().rules(new CodeFormat()).build().chain(CreateVehicle, CodeFormat).check(VALID)

    then:
    noExceptionThrown()
  }

  def 'a listener that throws when #at stops the check with its exception'() {
    given:
    def failure = new IllegalStateException('listener down')
    def failsAt = at
    def observer = new Observer() {
          void ruleStarted(String ruleId) {
            if (failsAt == 'a rule starts') {
              throw failure
            }
          }

          void ruleEnded(RuleEnd end) {
            if (failsAt == 'a rule ends') {
              throw failure
            }
          }

          void checkEnded(CheckEnd end) {
            if (failsAt == 'the check ends') {
              throw failure
            }
          }
        }
    def chain = Muster.builder().rules(new CodeFormat()).listener({
      observer
    }).build().chain(CreateVehicle, CodeFormat)

    when:
    chain.check(VALID)

    then:
    def e = thrown(IllegalStateException)
    e.is(failure)
    e.suppressed.length == 0

    where:
    at << ['a rule starts', 'a rule ends', 'the check ends']
  }

  def 'when the check is ending with an exception already, that one propagates and the listener\'s is suppressed: #what'() {
    given:
    def ends = []
    // What the observer throws when a rule ends and when the check ends, or null for nothing.
    def atRuleEnd = failures[0]
    def atCheckEnd = failures[1]
    def observer = new Observer() {
          void ruleEnded(RuleEnd end) {
            ends << end
            if (atRuleEnd != null) {
              throw atRuleEnd
            }
          }

          void checkEnded(CheckEnd end) {
            ends << end
            if (atCheckEnd != null) {
              throw atCheckEnd
            }
          }
        }
    def chain = Muster.builder().rules(rule).listener({
      observer
    }).build().chain(Object, rule.getClass())

    when:
    chain.check('input')

    then:
    def e = thrown(Throwable)
    e.getClass() == exception
    exception == BusinessRuleViolationException || e.is(rule instanceof Throwing ? rule.failure : failures[0])
    e.suppressed.toList() == suppressed
    ends*.outcome() == outcomes

    where:
    what                                                | rule                                       | failures || exception                      | suppressed       | outcomes
    'the rule threw, the listener throws twice'         | new Throwing(new IllegalStateException())  | [FIRST, SECOND] || IllegalStateException          | [FIRST, SECOND]  | [ERROR, ERROR]
    'the rule threw, the listener throws one twice'     | new Throwing(new IllegalStateException())  | [FIRST, FIRST] || IllegalStateException          | [FIRST]          | [ERROR, ERROR]
    'the rule threw, the listener throws at the end'    | new Throwing(new IllegalStateException())  | [null, FIRST] || IllegalStateException          | [FIRST]          | [ERROR, ERROR]
    'the check is rejected, the listener throws'        | new CodeFormatOfObject()                   | [null, FIRST] || BusinessRuleViolationException | [FIRST]          | [FAILED, FAILED]
    'the listener throws its exception again'           | new CodeFormatOfObject()                   | [FIRST, FIRST] || IllegalStateException          | []               | [FAILED, ERROR]
  }

  static final IllegalStateException FIRST = new IllegalStateException('listener down')
  static final IllegalStateException SECOND = new IllegalStateException('listener down again')

  def 'a listener that throws when a check starts stops the check before any rule'() {
    given:
    def failure = new IllegalStateException('listener down')
    def chain = Muster.builder().rules(new TaskExists(ran)).listener({
      throw failure
    }).build().chain(Long, TaskExists)

    when:
    chain.check(1L)

    then:
    def e = thrown(IllegalStateException)
    e.is(failure)
    ran.empty
  }

  def 'a check by type is named after the class of the input, a binding after its name or type'() {
    given:
    def muster = Muster.builder().rules(new CodeFormat()).listener(listener).build()

    when:
    muster.check(VALID)
    muster.binding(VehicleFields).name('save').build().check(VALID)
    muster.binding(VehicleFields).build().check(VALID)
    muster.collectionBinding(VehicleFields).name('save all').build().check([VALID])
    muster.collectionBinding(VehicleFields).build().check([VALID])
    muster.checkAll([VALID])

    then:
    listener.checks*.name() == [CreateVehicle.name, 'save', VehicleFields.name, 'save all', VehicleFields.name, CreateVehicle.name]
    listener.checks*.inputType() == [CreateVehicle] * 6
    listener.checks*.collection() == [false, false, false, true, true, true]
  }

  def 'a chain with its own mode keeps the listener'() {
    given:
    def chain = Muster.builder().rules(new CodeFormat()).listener(listener).build()
        .chain(CreateVehicle, CodeFormat).withMode(CheckMode.COLLECT_ALL)

    when:
    chain.check(VALID)

    then:
    listener.checks*.mode() == [CheckMode.COLLECT_ALL]
  }

  def 'a check of a collection tells its size, and each rule once: #what'() {
    given:
    def muster = Muster.builder().rules(new CodeFormat(), new NamesFree(), new BatchLimit(), new NamesDistinct())
        .listener(listener).mode(mode).build()
    def rejected = false

    when:
    try {
      muster.checkAll(inputs)
    } catch (BusinessRuleViolationException e) {
      rejected = true
    }

    then:
    rejected == (check == FAILED)
    listener.checks == [new Check(CreateVehicle.name, CreateVehicle, mode, true, inputs.size())]
    def expected = []
    [
      'vehicle.batch-limit': batchLimit, 'vehicle.code-format': codeFormat,
      'vehicle.names-distinct': namesDistinct, 'vehicle.names-free': namesFree,
    ].each { id, outcome ->
      if (outcome != null) {
        expected << ['started', id] << ['ended', id, outcome]
      }
    }
    listener.summary() == expected + [['check', check]]
    listener.ruleEnds()['vehicle.code-format'].violations()*.target()*.orElse('-') == codeFormatTargets

    where:
    what                           | mode                  | inputs || batchLimit | codeFormat | namesDistinct | namesFree | check  | codeFormatTargets
    'every element passes'         | CheckMode.FAIL_FAST   | [VALID, OTHER] || PASSED     | PASSED     | PASSED        | PASSED    | PASSED | []
    'an element fails, fail-fast'  | CheckMode.FAIL_FAST   | [VALID, INVALID] || PASSED     | FAILED     | null          | null      | FAILED | ['[1]']
    'elements fail, collect-all'   | CheckMode.COLLECT_ALL | [INVALID, OTHER, INVALID] || FAILED    | FAILED     | SKIPPED       | FAILED    | FAILED | ['[0]', '[2]']
    'the whole collection fails'   | CheckMode.COLLECT_ALL | [VALID, OTHER, THIRD] || FAILED     | PASSED     | SKIPPED       | PASSED    | FAILED | []
  }

  def 'an empty collection runs no rule, and the listener is not told of it'() {
    given:
    def muster = Muster.builder().rules(new CodeFormat()).listener(listener).build()

    when:
    muster.checkAll([])
    muster.chain(CreateVehicle, CodeFormat).checkAll([])
    muster.collectionBinding(CreateVehicle).build().check([])

    then:
    listener.checks.empty
  }

  def 'a rule of the elements that does not apply to a collection is skipped'() {
    given:
    def chain = Muster.builder().rules(new TaskImported(ran)).listener(listener).build().chain(Long, TaskImported)

    when:
    chain.checkAll([1L, 2L])

    then:
    listener.summary() == [['started', 'task.imported'], ['ended', 'task.imported', SKIPPED], ['check', PASSED]]
  }

  def 'a rule checked one by one after a rule that failed elements in collect-all mode gets the violations it found itself'() {
    given:
    def chain = Muster.builder().rules(new TaskExists(ran), new TaskLocked(ran)).listener(listener).mode(CheckMode.COLLECT_ALL).build()
        .chain(Long, TaskExists, TaskLocked)

    when:
    chain.checkAll([404L, 423L, 1L])

    then:
    thrown(BusinessRuleViolationException)
    listener.ruleEnds()['task.exists'].violations()*.target()*.get() == ['[0]']
    listener.ruleEnds()['task.locked'].violations()*.target()*.get() == ['[1]']
  }

  def 'a rule checked one by one after a rule that failed elements in collect-all mode passes when it finds none'() {
    given:
    def chain = Muster.builder().rules(new TaskExists(ran), new TaskLocked(ran)).listener(listener).mode(CheckMode.COLLECT_ALL).build()
        .chain(Long, TaskExists, TaskLocked)

    when:
    chain.checkAll([404L, 1L])

    then:
    thrown(BusinessRuleViolationException)
    listener.ruleEnds()['task.exists'].outcome() == FAILED
    listener.ruleEnds()['task.locked'].outcome() == PASSED
  }

  def 'a rule that checks the elements at once ends failed with its violations, in fail-fast mode too'() {
    given:
    def chain = Muster.builder().rules(new NamesFree()).listener(listener).build().chain(VehicleFields, NamesFree)

    when:
    chain.checkAll([VALID, INVALID])

    then:
    def e = thrown(BusinessRuleViolationException)
    listener.summary() == [['started', 'vehicle.names-free'], ['ended', 'vehicle.names-free', FAILED], ['check', FAILED]]
    listener.ruleEnds()['vehicle.names-free'].violations() == e.violations()
  }

  def 'a rule of a collection that throws ends with an error'() {
    given:
    def chain = Muster.builder().rules(new Throwing(new IllegalStateException())).listener(listener).build().chain(Object, Throwing)

    when:
    chain.checkAll(['a'])

    then:
    def e = thrown(IllegalStateException)
    listener.summary() == [['started', 'rule.throwing'], ['ended', 'rule.throwing', ERROR], ['check', ERROR]]
    listener.checkEnd().error().is(e)
  }

  /** Observes one check: what it is of, and the outcomes it is told. */
  static class OneCheck implements Observer {
    final Check check
    final List<Object> outcomes = []

    OneCheck(Check check) {
      this.check = check
    }

    void ruleEnded(RuleEnd end) {
      outcomes << end.ruleId() + ' ' + end.outcome()
    }

    void checkEnded(CheckEnd end) {
      outcomes << 'check ' + end.outcome()
    }
  }

  def 'checks on many threads at once each get an observer of their own, told of that check only'() {
    given:
    def observers = new CopyOnWriteArrayList<OneCheck>()
    def muster = Muster.builder().rules(new TaskExists(ran), new TaskLocked(ran)).mode(CheckMode.COLLECT_ALL)
        .listener({ check ->
          def observer = new OneCheck(check)
          observers << observer
          observer
        })
        .build()
    def chain = muster.chain(Long, TaskExists, TaskLocked)

    when:
    def rejected = concurrently { n ->
      try {
        chain.check(n % 2 == 0 ? 1L : 404L)
        false
      } catch (BusinessRuleViolationException e) {
        true
      }
    }

    then:
    rejected == (0..<16).collect { it % 2 == 1 }
    observers.size() == 16
    observers.every { it.check == new Check(Long.name, Long, CheckMode.COLLECT_ALL, false, 1) }
    observers.count {
      it.outcomes == ['task.exists PASSED', 'task.locked PASSED', 'check PASSED']
    } == 8
    observers.count {
      it.outcomes == ['task.exists FAILED', 'task.locked PASSED', 'check FAILED']
    } == 8
  }
}
