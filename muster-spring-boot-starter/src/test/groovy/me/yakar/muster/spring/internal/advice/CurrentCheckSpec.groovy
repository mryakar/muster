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
package me.yakar.muster.spring.internal.advice

import me.yakar.muster.Muster
import me.yakar.muster.rule.RuleTarget
import spock.lang.Specification

/** What each thread holds: nothing, the mark of a running check, or the check of its method. */
class CurrentCheckSpec extends Specification {

  def checked = Muster.builder().build().checkAll([])

  def cleanup() {
    CurrentCheck.restore(null)
  }

  def 'a thread holds nothing until a check starts on it'() {
    expect:
    CurrentCheck.get() == null
    !CurrentCheck.checking()
  }

  def 'a thread whose check runs holds no values yet'() {
    when:
    def previous = CurrentCheck.startCheck()

    then:
    previous == null
    CurrentCheck.checking()

    when:
    CurrentCheck.get()

    then:
    thrown(IllegalStateException)
  }

  def 'a check that passed is bound until the method returns, and then the thread holds what it held before'() {
    given:
    def outer = CurrentCheck.startCheck()
    CurrentCheck.bind(checked)

    when: 'a nested call checks and binds a check of its own'
    def inner = CurrentCheck.startCheck()
    def other = Muster.builder().build().check(new Plate())
    CurrentCheck.bind(other)

    then:
    inner.is(checked)
    CurrentCheck.get().is(other)

    when:
    CurrentCheck.restore(inner)

    then:
    CurrentCheck.get().is(checked)
    !CurrentCheck.checking()

    when:
    CurrentCheck.restore(outer)

    then:
    CurrentCheck.get() == null
  }

  static record Plate() implements RuleTarget {}
}
