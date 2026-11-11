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
package me.yakar.muster.spring

import static me.yakar.muster.spring.Concurrency.concurrently
import static me.yakar.muster.spring.Outcomes.failure
import static me.yakar.muster.spring.Outcomes.violated

import me.yakar.muster.spring.fixture.values.Garage
import me.yakar.muster.spring.fixture.values.InnerService
import me.yakar.muster.spring.fixture.values.LaterService
import me.yakar.muster.spring.fixture.values.PeeksAtValues
import me.yakar.muster.spring.fixture.values.SpotFound
import me.yakar.muster.spring.fixture.values.Vehicle
import me.yakar.muster.spring.fixture.values.VehicleFound
import me.yakar.muster.spring.fixture.values.VehicleService
import me.yakar.muster.spring.internal.advice.CurrentCheck
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.scheduling.annotation.EnableAsync
import spock.lang.Specification

/** The values that the rules of an advised method left, which the method reads from the bean. */
class CheckedValuesSpec extends Specification {

  static final String OUTSIDE = 'no @Rules method runs on this thread; the values of its check are there while it runs, on its own thread, and not before its check, after it returns, or on other threads, such as in an @Async method that it calls'

  static final String CHECKING = 'the rules of a @Rules method are being checked on this thread; a rule reads the values of the rules it requires from its context, with ctx.valueOf'

  @Configuration(proxyBeanMethods = false)
  @AutoConfigurationPackage(basePackageClasses = Garage)
  @Import([Garage, InnerService, VehicleService])
  static class Application {}

  @Configuration(proxyBeanMethods = false)
  @EnableAsync
  @Import(LaterService)
  static class Later {}

  def runner = new ApplicationContextRunner()
  .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))
  .withUserConfiguration(Application)

  static Vehicle amos(Long id) {
    new Vehicle(id, 'amo')
  }

  def 'an advised method reads the value a rule left, without reading the data again'() {
    expect:
    runner.run { context ->
      def service = context.getBean(VehicleService)
      assert service.rename(7L) == amos(7L)
      assert context.getBean(Garage).reads.get() == 1
      assert service.renameIfFound(7L)
      assert CurrentCheck.get() == null
    }
  }

  def 'an advised method tells whether a value rule that does not always apply left a value'() {
    expect:
    runner.run { context ->
      def service = context.getBean(VehicleService)
      assert service.parkIfFound(7L)
      context.getBean(SpotFound).applies = false
      assert !service.parkIfFound(7L)
    }
  }

  def 'an advised method that takes a collection reads the value of each element'() {
    expect:
    runner.run { context ->
      assert context.getBean(VehicleService).renameAll([7L, 9L, 3L]) == [amos(7L), amos(9L), amos(3L)]
      assert context.getBean(Garage).reads.get() == 3
    }
  }

  def 'the values are not there outside an advised method'() {
    expect:
    runner.run { context ->
      def values = context.getBean(CheckedValues)
      assert failure(IllegalStateException) { values.valueOf(VehicleFound) }.message == OUTSIDE
      assert failure(IllegalStateException) { values.hasValue(VehicleFound) }.message == OUTSIDE
      assert failure(IllegalStateException) { values.element(0) }.message == OUTSIDE
      assert values.toString() == 'CheckedValues[no @Rules method runs]'
    }
  }

  def 'the values are not there on another thread, as in an @Async method'() {
    expect:
    runner.run { context ->
      def failed = context.getBean(VehicleService).renameElsewhere(7L)
      assert failed instanceof IllegalStateException
      assert failed.message == OUTSIDE
    }
  }

  def 'an advised method called from another reads its own values, and the caller its own again once it returns'() {
    expect:
    runner.run { context ->
      assert context.getBean(VehicleService).renameAround(7L) == [amos(7L), amos(9L), amos(7L)]
      assert CurrentCheck.get() == null
    }
  }

  def 'a rule that reads the bean while it is checked finds no values, not even those of the method that called its own: #how'() {
    expect:
    runner.run { context ->
      call(context.getBean(VehicleService), context.getBean(InnerService))
      assert context.getBean(PeeksAtValues).seen == ['CheckedValues[the rules of a @Rules method are being checked]', CHECKING]
      assert CurrentCheck.get() == null
    }

    where:
    how                       | call
    'called on its own'       | { VehicleService outer, InnerService inner -> inner.peek(7L) }
    'called from another one' | { VehicleService outer, InnerService inner ->
      outer.renameAndPeek(7L)
    }
  }

  def 'an @Async method with rules reads its values on the thread that runs it'() {
    expect:
    runner.withUserConfiguration(Later).run { context ->
      assert context.getBean(LaterService).renameLater(7L).get() == amos(7L)
    }
  }

  def 'an advised method whose rules leave no value cannot read those of the method that called it'() {
    expect:
    runner.run { context ->
      def failed = failure(IllegalStateException) {
        context.getBean(VehicleService).renameThroughNoValues(7L)
      }
      assert failed.message == "no rule of the check leaves a value; ${VehicleFound.name} is not a value rule of it"
    }
  }

  def 'the values are gone once the method throws, or its check fails'() {
    expect:
    runner.run { context ->
      def service = context.getBean(VehicleService)
      assert failure(IllegalArgumentException) {
        service.renameAndFail(7L)
      }.message == 'renaming failed'
      assert CurrentCheck.get() == null
      assert violated { service.rename(12L) } == ['values.vehicle-owned']
      assert CurrentCheck.get() == null
    }
  }

  def 'the bean tells the values of the running method'() {
    expect:
    runner.run { context ->
      assert context.getBean(VehicleService).describe(7L) == 'CheckedValues[Checked[valueRules=[values.vehicle-found]]]'
    }
  }

  def 'each thread that calls an advised method at once reads the values of its own call, and keeps none after it'() {
    expect:
    runner.run { context ->
      def service = context.getBean(VehicleService)
      def mismatches = concurrently(16) { n ->
        (0..<100).findAll {
          def id = (n + it) % 5 * 2 + 1L
          def read = service.renameAround(id)
          read != [amos(id), amos(id + 2), amos(id)] || CurrentCheck.get() != null
        }
      }
      assert mismatches.flatten().empty
    }
  }
}
