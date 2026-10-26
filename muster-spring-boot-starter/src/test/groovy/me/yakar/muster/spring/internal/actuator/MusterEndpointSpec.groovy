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
package me.yakar.muster.spring.internal.actuator

import static me.yakar.muster.spring.Concurrency.concurrently

import me.yakar.muster.CheckMode
import me.yakar.muster.Muster
import me.yakar.muster.spring.MusterAutoConfiguration
import me.yakar.muster.spring.fixture.actuator.AxlesLimit
import me.yakar.muster.spring.fixture.actuator.BatchLimit
import me.yakar.muster.spring.fixture.actuator.Car
import me.yakar.muster.spring.fixture.actuator.GarageClosed
import me.yakar.muster.spring.fixture.actuator.GarageService
import me.yakar.muster.spring.fixture.actuator.IdKnown
import me.yakar.muster.spring.fixture.actuator.PlateFormat
import me.yakar.muster.spring.fixture.actuator.PlateFree
import me.yakar.muster.spring.fixture.actuator.Plated
import me.yakar.muster.spring.fixture.actuator.Truck
import me.yakar.muster.spring.fixture.actuator.WashService
import me.yakar.muster.spring.fixture.actuator.Washing
import me.yakar.muster.spring.fixture.actuator.other.WashService as OtherWash
import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import spock.lang.Specification

/** The actuator endpoint that lists the rules of every advised method. */
class MusterEndpointSpec extends Specification {

  static final String GARAGE = GarageService.name
  static final String CAR = Car.name
  static final String TRUCK = Truck.name

  @Configuration(proxyBeanMethods = false)
  @AutoConfigurationPackage(basePackageClasses = Car)
  static class Application {
    @Bean
    GarageService garageService() {
      new GarageService()
    }

    @Bean
    WashService washService() {
      new WashService()
    }

    @Bean
    OtherWash otherWash() {
      new OtherWash()
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class OwnMuster {
    @Bean
    Muster muster() {
      Muster.builder()
          .rules(new PlateFormat(), new PlateFree(), new AxlesLimit(), new GarageClosed(), new BatchLimit(), new IdKnown())
          .disable('garage.plate-free', 'garage.plate-format')
          .mode(CheckMode.COLLECT_ALL)
          .build()
    }
  }

  static final String EXPOSED = 'management.endpoints.web.exposure.include=muster'

  def runner = new ApplicationContextRunner()
  .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))
  .withUserConfiguration(Application)
  .withPropertyValues(EXPOSED, 'muster.rules.garage.closed.enabled=false')

  def 'the endpoint lists the mode, the disabled rules and every advised method once, with the chains bound so far'() {
    expect:
    runner.run { context ->
      def endpoint = context.getBean(MusterEndpoint)
      def report = endpoint.rules()
      assert report.mode() == CheckMode.FAIL_FAST
      assert report.disabled() == ['garage.closed']
      assert report.methods()*.method() == [
        'GarageService.inspect(Car)',
        'GarageService.park(Plated)',
        'GarageService.parkAll(List)',
        'GarageService.register(Car)',
        'GarageService.remove(Long)',
        'WashService.wash(Car)',
        'WashService.wash(Car)'
      ]
      assert report.methods()*.target() == [GARAGE] * 5 + [WashService.name, OtherWash.name]
      assert report.methods()*.input() == [CAR, Plated.name, CAR, CAR, Long.name, CAR, CAR]
      assert report.methods()*.collection() == [false, false, true, false, false, false, false]
      assert report.methods()*.mode() == [CheckMode.FAIL_FAST] * 4 + [CheckMode.COLLECT_ALL, CheckMode.FAIL_FAST, CheckMode.FAIL_FAST]
      assert report.methods()*.rules() == [[], [], [], [], ['garage.id-known'], [], []]
      assert report.methods()*.excluded() == [['garage.plate-free'], [], [], [], [], [], []]
      assert report.methods()*.chains() == [
        [(CAR): ['garage.plate-format']],
        [:],
        [(CAR): ['garage.batch-limit', 'garage.plate-format', 'garage.plate-free']],
        [(CAR): ['garage.plate-format', 'garage.plate-free']],
        [(Long.name): ['garage.id-known']],
        [(CAR): ['garage.plate-format', 'garage.plate-free']],
        [(CAR): ['garage.plate-format', 'garage.plate-free']]
      ]
      assert report.types() == [:]
      assert report.collections() == [:]

      // The method of a type that is not final is called with two classes, one through the interface of its bean.
      def service = context.getBean(GarageService)
      service.park(new Truck('TR-1', 2))
      service.park(new Car('TR-2'))
      context.getBean('washService', Washing).wash(new Car('TR-3'))
      def later = endpoint.rules()

      // Their chains are listed, by name; the earlier report is unchanged.
      assert later.methods()[1].chains() == [
        (CAR): ['garage.plate-format', 'garage.plate-free'],
        (TRUCK): ['garage.axles', 'garage.plate-format', 'garage.plate-free']
      ]
      assert later.methods()*.method() == report.methods()*.method()
      assert report.methods()[1].chains() == [:]
    }
  }

  def 'the endpoint lists the chains Muster bound by type, for single targets and for collections apart'() {
    expect:
    runner.run { context ->
      def muster = context.getBean(Muster)
      muster.check(new Truck('TR-1', 2))
      muster.checkAll([new Car('TR-2')])
      def report = context.getBean(MusterEndpoint).rules()
      assert report.types() == [(TRUCK): ['garage.axles', 'garage.plate-format', 'garage.plate-free']]
      assert report.collections() == [(CAR): ['garage.batch-limit', 'garage.plate-format', 'garage.plate-free']]
    }
  }

  def 'the report cannot be changed'() {
    expect:
    runner.run { context ->
      def report = context.getBean(MusterEndpoint).rules()
      [
        report.disabled(),
        report.methods(),
        report.types(),
        report.collections(),
        report.methods()[0].chains()
      ].each {
        try {
          it.clear()
          assert false
        } catch (UnsupportedOperationException expected) {
        }
      }
    }
  }

  def 'an application with its own Muster is listed with that Muster'() {
    expect:
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))
        .withUserConfiguration(Application, OwnMuster)
        .withPropertyValues(EXPOSED)
        .run { context ->
          def report = context.getBean(MusterEndpoint).rules()
          assert report.mode() == CheckMode.COLLECT_ALL
          assert report.disabled() == ['garage.plate-format', 'garage.plate-free']
          assert report.methods()[3].chains() == [(CAR): ['garage.closed']]
        }
  }

  def 'without the actuator there is no endpoint, and the application starts'() {
    expect:
    runner.withClassLoader(new FilteredClassLoader(Endpoint)).run { context ->
      assert context.startupFailure == null
      assert !context.containsBean('musterEndpoint')
      assert context.getBean(GarageService) != null
    }
  }

  def 'the endpoint is left out as the actuator leaves its own out: #why'() {
    expect:
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))
        .withUserConfiguration(Application)
        .withPropertyValues(*properties)
        .run { context ->
          assert context.startupFailure == null
          assert !context.containsBean('musterEndpoint')
        }

    where:
    why                      | properties
    'not exposed'            | []
    'no access'              | [EXPOSED, 'management.endpoint.muster.access=NONE']
    'switched off, as before' | [EXPOSED, 'management.endpoint.muster.enabled=false']
  }

  def 'the listing is read while other threads bind new classes'() {
    expect:
    runner.run { context ->
      def service = context.getBean(GarageService)
      def endpoint = context.getBean(MusterEndpoint)
      def seen = concurrently { n ->
        if (n % 2 == 0) {
          service.park(n % 4 == 0 ? new Car("TR-$n") : new Truck("TR-$n", 2))
          return null
        }
        endpoint.rules().methods()[1].chains().keySet().toList()
      }
      assert seen.findAll { it != null }.every { it in [[], [CAR], [TRUCK], [CAR, TRUCK]] }
      assert endpoint.rules().methods()[1].chains().keySet().toList() == [CAR, TRUCK]
    }
  }
}
