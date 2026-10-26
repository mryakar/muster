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

import me.yakar.muster.ObservedCheckSpec.Recording
import me.yakar.muster.spi.CheckListener

/** Several listeners of one Muster: told in order, each with its own observer. */
class CheckListenersSpec extends EngineSpecification {

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

  def 'a builder rejects a null listener'() {
    when:
    Muster.builder().listener(null)

    then:
    def e = thrown(NullPointerException)
    e.message == 'listener'
  }
}
