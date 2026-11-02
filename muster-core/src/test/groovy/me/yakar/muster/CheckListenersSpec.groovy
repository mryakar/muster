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

import static me.yakar.muster.spi.CheckListener.Outcome.ERROR
import static me.yakar.muster.spi.CheckListener.Outcome.PASSED

import java.time.Duration
import me.yakar.muster.ObservedCheckSpec.Recording
import me.yakar.muster.ObservedCheckSpec.Throwing
import me.yakar.muster.spi.CheckListener
import me.yakar.muster.spi.CheckListener.CheckEnd
import me.yakar.muster.spi.CheckListener.RuleEnd
import me.yakar.muster.rule.BusinessRule

/** Several listeners of one Muster: told in order, each with its own observer. */
class CheckListenersSpec extends EngineSpecification {

  /** Records what it is told, as Recording does, and then throws when told of one kind of event. */
  static class ThrowingAt extends Recording {
    final String at
    final Throwable failure

    ThrowingAt(String at, Throwable failure) {
      this.at = at
      this.failure = failure
    }

    void ruleStarted(String ruleId) {
      super.ruleStarted(ruleId)
      failIf('ruleStarted')
    }

    void ruleEnded(RuleEnd end) {
      super.ruleEnded(end)
      failIf('ruleEnded')
    }

    void checkEnded(CheckEnd end) {
      super.checkEnded(end)
      failIf('checkEnded')
    }

    private void failIf(String event) {
      if (at == event) {
        throw failure
      }
    }
  }

  /** Records what it is told, and throws again the exception a rule or the check ended with. */
  static class Rethrowing extends Recording {
    void ruleEnded(RuleEnd end) {
      super.ruleEnded(end)
      throw end.error()
    }

    void checkEnded(CheckEnd end) {
      super.checkEnded(end)
      throw end.error()
    }
  }

  // New for every feature, since suppressing an exception on another changes it.
  def first = new IllegalStateException('first listener down')
  def second = new IllegalArgumentException('second listener down')

  static Muster musterOf(List<CheckListener> listeners, BusinessRule<?>... rules) {
    def builder = Muster.builder().rules(rules)
    listeners.each { builder.listener(it) }
    builder.build()
  }

  def 'no listener is none, and one listener is itself'() {
    given:
    def listener = new Recording()

    expect:
    CheckListeners.of([]) == null
    CheckListeners.of([listener]).is(listener)
  }

  def 'every listener is told about a check, in the order they were added'() {
    given:
    def told = []
    def first = new Recording() {
          CheckListener.Observer checkStarted(CheckListener.Check check) {
            told << 'first'
            super.checkStarted(check)
          }
        }
    def second = new Recording() {
          CheckListener.Observer checkStarted(CheckListener.Check check) {
            told << 'second'
            super.checkStarted(check)
          }
        }
    def chain = Muster.builder().rules(new CodeFormat()).listener(first).listener(second).build().chain(CreateVehicle, CodeFormat)

    when:
    chain.check(INVALID)

    then:
    thrown(BusinessRuleViolationException)
    told == ['first', 'second']
    first.summary() == second.summary()
    first.summary()*.get(0) == ['started', 'ended', 'check']
  }

  def 'a listener that observes nothing of a check leaves the others to it'() {
    given:
    def observing = new Recording()
    def chain = Muster.builder().rules(new CodeFormat())
        .listener({ null }).listener(observing).listener({ null })
        .build().chain(CreateVehicle, CodeFormat)

    when:
    chain.check(VALID)

    then:
    observing.summary()*.get(0) == ['started', 'ended', 'check']
  }

  def 'when no listener observes a check, nothing more is done for it'() {
    given:
    def composite = CheckListeners.of([{ null } as CheckListener, { null } as CheckListener])

    expect:
    composite.checkStarted(new CheckListener.Check('x', String, CheckMode.FAIL_FAST, false, 1)) == null
  }

  def 'every observer is told that the check ends when one throws, and the first exception propagates with the later ones suppressed'() {
    given:
    def listeners = [new ThrowingAt('checkEnded', first), new ThrowingAt('checkEnded', second), new Recording()]
    def chain = musterOf(listeners, new CodeFormat()).chain(CreateVehicle, CodeFormat)

    when:
    chain.check(VALID)

    then:
    def e = thrown(IllegalStateException)
    e.is(first)
    e.suppressed.toList() == [second]
    listeners.every {
      it.summary() == [['started', 'vehicle.code-format'], ['ended', 'vehicle.code-format', PASSED], ['check', PASSED]]
    }
  }

  def 'every observer is told that a rule ends when one throws, and then that the check ended with that exception'() {
    given:
    def listeners = [new ThrowingAt('ruleEnded', first), new Recording(), new Recording()]
    def chain = musterOf(listeners, new CodeFormat()).chain(CreateVehicle, CodeFormat)

    when:
    chain.check(VALID)

    then:
    def e = thrown(IllegalStateException)
    e.is(first)
    e.suppressed.length == 0
    listeners.every {
      it.summary() == [['started', 'vehicle.code-format'], ['ended', 'vehicle.code-format', PASSED], ['check', ERROR]]
    }
    listeners.every { it.checkEnd().error().is(first) }
  }

  def 'when a rule threw, observers that throw add their exceptions to it once, and one that throws it again adds nothing'() {
    given:
    def failure = new IllegalStateException('rule down')
    def listeners = [
      new ThrowingAt('ruleEnded', first),
      new Rethrowing(),
      new ThrowingAt('checkEnded', first),
      new Recording()
    ]
    def chain = musterOf(listeners, new Throwing(failure)).chain(Object, Throwing)

    when:
    chain.check('input')

    then:
    def e = thrown(IllegalStateException)
    e.is(failure)
    e.suppressed.toList() == [first]
    first.suppressed.length == 0
    listeners.every {
      it.summary() == [['started', 'rule.throwing'], ['ended', 'rule.throwing', ERROR], ['check', ERROR]]
    }
  }

  def 'an observer that throws when a rule starts stops the check; the observers told before it are told that the rule ended with its exception'() {
    given:
    def told = new Recording()
    def failing = new ThrowingAt('ruleStarted', first)
    def after = new Recording()
    def chain = musterOf([told, failing, after], new TaskExists(ran)).chain(Long, TaskExists)

    when:
    chain.check(1L)

    then:
    def e = thrown(IllegalStateException)
    e.is(first)
    ran.empty
    told.summary() == [['started', 'task.exists'], ['ended', 'task.exists', ERROR], ['check', ERROR]]
    told.ruleEnds()['task.exists'].error().is(first)
    told.ruleEnds()['task.exists'].took() == Duration.ZERO
    failing.summary() == [['started', 'task.exists'], ['check', ERROR]]
    after.summary() == [['check', ERROR]]
    [told, failing, after].every { it.checkEnd().error().is(first) }
  }

  def 'a listener that throws when a check starts stops it; the observers returned before it are told that the check ended with its exception'() {
    given:
    def ending = new ThrowingAt('checkEnded', second)
    def observing = new Recording()
    def chain = musterOf([ending, observing, {
        throw first
      } as CheckListener], new TaskExists(ran)).chain(Long, TaskExists)

    when:
    chain.check(1L)

    then:
    def e = thrown(IllegalStateException)
    e.is(first)
    e.suppressed.toList() == [second]
    ran.empty
    [ending, observing].every {
      it.summary() == [['check', ERROR]] && it.checkEnd().error().is(first) && it.checkEnd().took() == Duration.ZERO
    }
  }

  def 'a first listener that throws when a check starts leaves the later ones unasked'() {
    given:
    def later = new Recording()
    def chain = musterOf([{
        throw first
      } as CheckListener, later], new TaskExists(ran)).chain(Long, TaskExists)

    when:
    chain.check(1L)

    then:
    def e = thrown(IllegalStateException)
    e.is(first)
    e.suppressed.length == 0
    later.checks.empty
    ran.empty
  }

  def 'a builder rejects a null listener'() {
    when:
    Muster.builder().listener(null)

    then:
    def e = thrown(NullPointerException)
    e.message == 'listener'
  }
}
