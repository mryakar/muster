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
package me.yakar.muster.spi

import java.time.Duration
import me.yakar.muster.CheckMode
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.spi.CheckListener.Check
import me.yakar.muster.spi.CheckListener.CheckEnd
import me.yakar.muster.spi.CheckListener.Observer
import me.yakar.muster.spi.CheckListener.Outcome
import me.yakar.muster.spi.CheckListener.RuleEnd
import spock.lang.Specification

/** The events a listener is given: what they reject, and what they keep. */
class CheckListenerSpec extends Specification {

  def violation = RuleContext.forRule('vehicle.name-unique').violation().build()

  def 'an observer does nothing unless a method is overridden'() {
    given:
    def observer = new Observer() {}

    when:
    observer.ruleStarted('vehicle.name-unique')
    observer.ruleEnded(new RuleEnd('vehicle.name-unique', Outcome.PASSED, Duration.ZERO, [], null))
    observer.checkEnded(new CheckEnd(Outcome.PASSED, Duration.ZERO, [], null))

    then:
    noExceptionThrown()
  }

  def 'an end keeps a copy of the violations'() {
    given:
    def violations = [violation]

    when:
    def rule = new RuleEnd('vehicle.name-unique', Outcome.FAILED, Duration.ZERO, violations, null)
    def check = new CheckEnd(Outcome.FAILED, Duration.ZERO, violations, null)
    violations.clear()

    then:
    rule.violations() == [violation]
    check.violations() == [violation]
  }

  def 'a check rejects a null #name'() {
    when:
    new Check(name == 'name' ? null : 'x', name == 'inputType' ? null : String, name == 'mode' ? null : CheckMode.FAIL_FAST, false, 1)

    then:
    def e = thrown(NullPointerException)
    e.message == name

    where:
    name << ['name', 'inputType', 'mode']
  }

  def 'the end of a rule rejects a null #name'() {
    when:
    new RuleEnd(name == 'ruleId' ? null : 'x', name == 'outcome' ? null : Outcome.PASSED, name == 'took' ? null : Duration.ZERO, name == 'violations' ? null : [], null)

    then:
    def e = thrown(NullPointerException)
    e.message == name

    where:
    name << ['ruleId', 'outcome', 'took', 'violations']
  }

  def 'the end of a check rejects a null #name'() {
    when:
    new CheckEnd(name == 'outcome' ? null : Outcome.PASSED, name == 'took' ? null : Duration.ZERO, name == 'violations' ? null : [], null)

    then:
    def e = thrown(NullPointerException)
    e.message == name

    where:
    name << ['outcome', 'took', 'violations']
  }
}
