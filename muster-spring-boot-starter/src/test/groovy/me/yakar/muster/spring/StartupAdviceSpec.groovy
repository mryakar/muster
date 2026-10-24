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

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import me.yakar.muster.BusinessRuleViolationException
import me.yakar.muster.spring.fixture.advice.NameFree
import me.yakar.muster.spring.fixture.advice.SaveVehicle
import me.yakar.muster.spring.fixture.advice.VehicleService
import me.yakar.muster.spring.fixture.eager.CallsServiceWhenCreated
import me.yakar.muster.spring.fixture.lazy.Fleet
import me.yakar.muster.spring.fixture.lazy.Plates
import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Scope
import spock.lang.Specification

/** Rules apply to calls made while the application starts, and to beans created after it. */
class StartupAdviceSpec extends Specification {

  @Configuration(proxyBeanMethods = false)
  @AutoConfigurationPackage(basePackageClasses = NameFree)
  @Import(VehicleService)
  static class Application {}

  /** Calls an advised service when it is initialized, as code that seeds data does. */
  @Configuration(proxyBeanMethods = false)
  static class Seeding {
    @Bean
    InitializingBean seeder(VehicleService service, SeedingInput input) {
      { -> service.save(input.vehicle) } as InitializingBean
    }
  }

  static class SeedingInput {
    SaveVehicle vehicle
  }

  @Configuration(proxyBeanMethods = false)
  @AutoConfigurationPackage(basePackageClasses = Plates)
  static class LazyApplication {
    @Bean
    static LazyInitializationBeanFactoryPostProcessor lazyInitialization() {
      new LazyInitializationBeanFactoryPostProcessor()
    }
  }

  @Configuration(proxyBeanMethods = false)
  @AutoConfigurationPackage(basePackageClasses = CallsServiceWhenCreated)
  static class RuleCallsServiceWhenCreated {}

  @Configuration(proxyBeanMethods = false)
  static class PrototypeService {
    @Bean
    @Scope('prototype')
    VehicleService prototypeService() {
      new VehicleService()
    }
  }

  def runner = new ApplicationContextRunner()
  .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))

  def 'a call while the application starts checks the rules: #name'() {
    expect:
    runner.withUserConfiguration(Application, Seeding)
        .withBean(SeedingInput, {
          -> new SeedingInput(vehicle: new SaveVehicle(name))
        })
        .run { context ->
          if (passes) {
            assert context.startupFailure == null
          } else {
            assert MusterAutoConfigurationSpec.rootCause(context.startupFailure) instanceof BusinessRuleViolationException
          }
        }

    where:
    name || passes
    'Boreas' || true
    'Atlas' || false
  }

  def 'with lazy initialization, a rule may depend on an advised service'() {
    expect:
    runner.withUserConfiguration(LazyApplication).withBean(Plates).withBean(Fleet).run { context ->
      assert context.startupFailure == null
      def fleet = context.getBean(Fleet)
      fleet.retire(1L)
      try {
        fleet.retire(404L)
        assert false: 'the call passed'
      } catch (BusinessRuleViolationException e) {
        assert e.violations()*.code() == ['lazy.plate-known']
      }
    }
  }

  def 'a call of an advised method while Muster is created fails and says why'() {
    when:
    runner.withUserConfiguration(LazyApplication, RuleCallsServiceWhenCreated).withBean(Plates).withBean(Fleet).run { context ->
      context.getBean(Fleet)
    }

    then: 'the failure comes when Muster is created'
    def e = thrown(Exception)
    MusterAutoConfigurationSpec.rootCause(e).message.endsWith(
        'is called while Muster is created, before its rules can be prepared; do not call advised methods from the constructors of rules or of the beans they need')
  }

  def 'beans created at once from many threads after the start check their rules'() {
    given:
    def threads = 16
    def start = new CountDownLatch(1)
    def pool = Executors.newFixedThreadPool(threads)

    expect:
    runner.withUserConfiguration(Application, PrototypeService).run { context ->
      def tasks = (0..<threads).collect { {

          ->
          start.await()
          def service = context.getBean('prototypeService', VehicleService)
          try {
            service.save(new SaveVehicle('Atlas'))
            'passed'
          } catch (BusinessRuleViolationException e) {
            e.violations()*.code().join()
          }
        } as Callable<String>
      }
      def futures = tasks.collect { pool.submit(it) }
      start.countDown()
      assert futures.collect {
        it.get(1, TimeUnit.MINUTES)
      }.toSet() == ['advice.name-free'] as Set
    }

    cleanup:
    pool.shutdownNow()
  }
}
