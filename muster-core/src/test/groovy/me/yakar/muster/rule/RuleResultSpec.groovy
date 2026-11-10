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

import spock.lang.Shared
import spock.lang.Specification

class RuleResultSpec extends Specification {

  @Shared
  def context = RuleContext.forRule('plan.capacity-sufficient')
  @Shared
  def passengers = context.violation('plan.passenger-capacity').build()
  @Shared
  def parcels = context.violation('plan.parcel-capacity').build()

  def 'every passed result without a value is the same instance'() {
    expect:
    RuleResult.passed() instanceof RuleResult.Passed
    RuleResult.passed().is(RuleResult.passed())
    (RuleResult.passed() as RuleResult.Passed).value() == null
    new RuleResult.Passed() == RuleResult.passed()
  }

  def 'a passed result carries the value of a value rule'() {
    given:
    def vehicle = new StringBuilder('Atlas')

    expect:
    (RuleResult.passed(vehicle) as RuleResult.Passed).value().is(vehicle)
    RuleResult.passed(vehicle) == RuleResult.passed(vehicle)
    RuleResult.passed(vehicle) != RuleResult.passed()
  }

  def 'a passed result rejects a null value'() {
    when:
    RuleResult.passed((Object) null)

    then:
    def e = thrown(NullPointerException)
    e.message == 'value'
  }

  def 'a failed result keeps the violations in order'() {
    expect:
    (RuleResult.failed(passengers) as RuleResult.Failed).violations() == [passengers]
    (RuleResult.failed(passengers, parcels) as RuleResult.Failed).violations() == [passengers, parcels]
    (RuleResult.failed([parcels, passengers]) as RuleResult.Failed).violations() == [parcels, passengers]
  }

  def 'a failed result does not change when the given list changes'() {
    given:
    def violations = [passengers]
    def result = RuleResult.failed(violations) as RuleResult.Failed

    when:
    violations << parcels

    then:
    result.violations() == [passengers]
  }

  def 'the violations of a failed result cannot be changed'() {
    given:
    def result = RuleResult.failed(passengers) as RuleResult.Failed

    when:
    result.violations().add(parcels)

    then:
    thrown(UnsupportedOperationException)
  }

  def 'a failed result needs at least one violation'() {
    when:
    RuleResult.failed([])

    then:
    def e = thrown(IllegalArgumentException)
    e.message == 'a failed result needs at least one violation'
  }

  def 'a failed result rejects null'() {
    when:
    create()

    then:
    thrown(NullPointerException)

    where:
    create << [
      { RuleResult.failed((List) null) },
      { RuleResult.failed([passengers, null]) },
      { RuleResult.failed(passengers, (Violation) null) },
      { RuleResult.failed((Violation) null) }
    ]
  }

  def 'the result of a collection keeps the result of each input in order'() {
    given:
    def failed = RuleResult.failed(passengers)

    when:
    def results = [RuleResult.passed('a'), failed, RuleResult.passed()]
    def result = RuleResult.ofElements(results) as RuleResult.Elements
    results.clear()

    then:
    result.results() == [RuleResult.passed('a'), failed, RuleResult.passed()]
  }

  def 'the result of a collection needs the result of an input: #problem'() {
    when:
    RuleResult.ofElements(results)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == message

    where:
    problem         | results || message
    'none'          | [] || 'the result of a collection needs a result per input'
    'a collection'  | [RuleResult.passed(), RuleResult.ofElements([RuleResult.passed()])] || 'the result of an input is passed or failed, not the result of a collection'
  }

  def 'the result of a collection rejects null'() {
    when:
    RuleResult.ofElements(results)

    then:
    thrown(NullPointerException)

    where:
    results << [null, [RuleResult.passed(), null]]
  }
}
