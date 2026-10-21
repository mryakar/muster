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

import spock.lang.Shared
import spock.lang.Specification

class RuleResultSpec extends Specification {

  @Shared
  def context = RuleContext.forRule('plan.capacity-sufficient')
  @Shared
  def passengers = context.violation('plan.passenger-capacity').build()
  @Shared
  def parcels = context.violation('plan.parcel-capacity').build()

  def 'every passed result is the same instance'() {
    expect:
    RuleResult.passed() instanceof RuleResult.Passed
    RuleResult.passed().is(RuleResult.passed())
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
}
