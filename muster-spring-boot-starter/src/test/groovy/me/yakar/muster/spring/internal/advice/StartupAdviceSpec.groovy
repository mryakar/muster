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

import static me.yakar.muster.spring.Outcomes.violated
import static me.yakar.muster.spring.Concurrency.concurrently
import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause

import java.util.concurrent.TimeUnit
import me.yakar.muster.BusinessRuleViolationException
import me.yakar.muster.spring.MusterAutoConfiguration
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
import java.util.concurrent.CompletableFuture
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import me.yakar.muster.spring.fixture.advice.VehicleComponent
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Lazy
import me.yakar.muster.spring.LogCapture
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
  @AutoConfigurationPackage(basePackageClasses = NameFree)
  static class RulePackage {}

  @Configuration(proxyBeanMethods = false)
  static class PrototypeService {
    @Bean
    @Scope('prototype')
    VehicleService prototypeService() {
      new VehicleService()
    }
  }

  /**
   * A rule whose creation lets another thread call an advised method, which needs this rule, and
   * then creates an advised bean.
   */
  @Rule(id = 'startup.slow')
  static class SlowRule implements BusinessRule<SaveVehicle> {
    static volatile Thread caller

    SlowRule(VehicleService service, ObjectProvider<VehicleComponent> component) {
      caller = Thread.startDaemon('advised-caller') {
        service.save(new SaveVehicle('Boreas'))
      }
      // The caller prepares the service and waits for Spring to finish this rule.
      def deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
      while (!(caller.state == Thread.State.WAITING && caller.stackTrace.any {
            it.methodName == 'prepareNoted'
          })
          && caller.alive && System.nanoTime() <deadline) {
        Thread.onSpinWait()
      }
      component.getObject()
    }

    RuleResult check(SaveVehicle input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  @Configuration(proxyBeanMethods = false)
  @AutoConfigurationPackage(basePackageClasses = NameFree)
  static class AdvisedCallOnAnotherThread {
    @Bean
    VehicleService vehicleService() {
      new VehicleService()
    }

    @Bean
    @Lazy
    VehicleComponent vehicleComponent() {
      new VehicleComponent()
    }

    @Bean
    SlowRule slowRule(VehicleService service, ObjectProvider<VehicleComponent> component) {
      new SlowRule(service, component)
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
            assert getMostSpecificCause(context.startupFailure) instanceof BusinessRuleViolationException
          }
        }

    where:
    name || passes
    'Boreas' || true
    'Atlas' || false
  }

  def 'with lazy initialization, a rule may depend on an advised service'() {
    when:
    runner.withUserConfiguration(LazyApplication).withBean(Plates).withBean(Fleet).run { context ->
      assert context.startupFailure == null
      def fleet = context.getBean(Fleet)
      fleet.retire(1L)
      fleet.retire(404L)
    }

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.code() == ['lazy.plate-known']
  }

  def 'a call of an advised method while Muster is created fails and says why'() {
    when:
    runner.withUserConfiguration(LazyApplication, RuleCallsServiceWhenCreated).withBean(Plates).withBean(Fleet).run { context ->
      context.getBean(Fleet)
    }

    then: 'the failure comes when Muster is created'
    def e = thrown(Exception)
    getMostSpecificCause(e).message == (
        'Plates.known(String) is called while Muster is created, before its rules can be prepared; do not call advised methods from the constructors of rules or of the beans they need')
  }

  def 'beans of a class created at once from many threads after the start prepare it once and check their rules'() {
    given:
    def logs = new LogCapture()

    expect: 'no bean of the class exists before'
    runner.withUserConfiguration(RulePackage, PrototypeService).run { context ->
      def outcomes = concurrently {
        violated {
          context.getBean('prototypeService', VehicleService).save(new SaveVehicle('Atlas'))
        }
      }
      assert outcomes.toSet() == [['advice.name-free']] as Set
    }
    logs.messages.count {
      it.startsWith('Rules bound to VehicleService.save(SaveVehicle)')
    } == 1

    cleanup:
    logs.close()
  }

  def 'a call on another thread that prepares rules while the application starts does not block the start'() {
    when:
    def started = CompletableFuture.supplyAsync {
      runner.withUserConfiguration(AdvisedCallOnAnotherThread).run {
        assert it.startupFailure == null
      }
      true
    }

    then:
    started.get(1, TimeUnit.MINUTES)
  }
}
