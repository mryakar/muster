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
package me.yakar.muster.spring.internal.selfinvocation

import java.util.logging.Level
import me.yakar.muster.spring.LogCapture
import spock.lang.Specification

/** What each mode does with the calls through this that a scan finds. */
class SelfInvocationModeSpec extends Specification {

  static final List<SelfInvocation> FOUND = [
    new SelfInvocation('Garage.saver()', 'Garage.save(Vehicle)'),
    new SelfInvocation('Garage.register(String)', 'Garage.save(Vehicle)'),
    new SelfInvocation('Garage.register(String)', 'Garage.park(Vehicle)')
  ]

  def 'in fail mode, the calls found fail, named in the order of their callers'() {
    when:
    SelfInvocationMode.FAIL.requireNone(FOUND)

    then:
    def e = thrown(IllegalStateException)
    e.message == 'Muster found calls of advised methods through this, which pass no proxy and check no rules: ' +
        'Garage.register(String) calls Garage.park(Vehicle) through this; ' +
        'Garage.register(String) calls Garage.save(Vehicle) through this; ' +
        'Garage.saver() calls Garage.save(Vehicle) through this. ' +
        'Call the method through its bean: inject the bean itself, with @Lazy or an ObjectProvider, or move the method to another bean; or set muster.self-invocation=warn to log such calls instead'
  }

  def 'the calls found pass in #mode mode'() {
    when:
    mode.requireNone(FOUND)

    then:
    noExceptionThrown()

    where:
    mode << [SelfInvocationMode.WARN, SelfInvocationMode.OFF]
  }

  def 'no call found passes in #mode mode'() {
    when:
    mode.requireNone([])

    then:
    noExceptionThrown()

    where:
    mode << SelfInvocationMode.values()
  }

  def 'each call found is logged at WARNING, in the order of their callers'() {
    given:
    def logs = new LogCapture()

    when:
    SelfInvocationMode.WARN.warn(FOUND)

    then:
    logs.records*.level == [Level.WARNING] * 3
    logs.messages == [
      'Garage.register(String) calls Garage.park(Vehicle) through this',
      'Garage.register(String) calls Garage.save(Vehicle) through this',
      'Garage.saver() calls Garage.save(Vehicle) through this'
    ].collect {
      "$it, which passes no proxy and checks no rules; call the method through its bean".toString()
    }

    cleanup:
    logs.close()
  }

  def 'in a native image, #mode mode warns that it cannot scan, and scans nothing'() {
    given:
    def logs = new LogCapture()

    when:
    def scanning = logs.withCloseable { mode.inNativeImage(true) }

    then:
    scanning == SelfInvocationMode.OFF
    logs.records*.level == [Level.WARNING]
    logs.messages == [
      'In a native image, Muster cannot read class files, so it does not look for methods that call an advised method through this, which checks no rules; set muster.self-invocation=off to leave this warning out'
    ]

    where:
    mode << [SelfInvocationMode.FAIL, SelfInvocationMode.WARN]
  }

  def 'in a native image, off mode logs nothing'() {
    given:
    def logs = new LogCapture()

    expect:
    logs.withCloseable { SelfInvocationMode.OFF.inNativeImage(true) } == SelfInvocationMode.OFF
    logs.records.empty
  }

  def 'outside a native image, #mode mode scans as it is'() {
    given:
    def logs = new LogCapture()

    expect:
    logs.withCloseable { mode.inNativeImage(false) } == mode
    logs.records.empty

    where:
    mode << SelfInvocationMode.values()
  }
}
