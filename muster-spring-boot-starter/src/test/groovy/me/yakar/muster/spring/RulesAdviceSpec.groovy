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

import java.lang.reflect.Proxy
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import me.yakar.muster.BusinessRuleViolationException
import me.yakar.muster.spring.fixture.advice.FinalMethod
import me.yakar.muster.spring.fixture.advice.LazyVehicleService
import me.yakar.muster.spring.fixture.advice.NameFree
import me.yakar.muster.spring.fixture.advice.NoParameter
import me.yakar.muster.spring.fixture.advice.NotPublic
import me.yakar.muster.spring.fixture.advice.OperationsService
import me.yakar.muster.spring.fixture.advice.RenameVehicle
import me.yakar.muster.spring.fixture.advice.SaveVehicle
import me.yakar.muster.spring.fixture.advice.StaticMethod
import me.yakar.muster.spring.fixture.advice.TwoModes
import me.yakar.muster.spring.fixture.advice.TwoParametersWithRules
import me.yakar.muster.spring.fixture.advice.TwoParameters
import me.yakar.muster.spring.fixture.advice.UnregisteredRule
import me.yakar.muster.spring.fixture.advice.VehicleComponent
import me.yakar.muster.spring.fixture.advice.VehicleOperations
import me.yakar.muster.spring.fixture.advice.VehicleService
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.Ordered
import spock.lang.Specification

class RulesAdviceSpec extends Specification {

  static final SaveVehicle FREE = new SaveVehicle('Boreas')
  static final SaveVehicle TAKEN = new SaveVehicle('Atlas')
  static final SaveVehicle TAKEN_AND_LONG = new SaveVehicle('Atlas'.padRight(11, '!'))

  @Configuration(proxyBeanMethods = false)
  @AutoConfigurationPackage(basePackageClasses = NameFree)
  @Import([VehicleService, VehicleComponent])
  static class Application {}

  @Configuration(proxyBeanMethods = false)
  @AutoConfigurationPackage(basePackageClasses = NameFree)
  static class Rules {}

  def runner = new ApplicationContextRunner()
  .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))
  .withUserConfiguration(Application)

  static List<String> violated(Closure call) {
    try {
      call()
      []
    } catch (BusinessRuleViolationException e) {
      e.violations()*.code()
    }
  }

  def 'a service method checks the rules bound to its input by type before it runs'() {
    expect:
    runner.run { context ->
      def service = context.getBean(VehicleService)
      assert AopUtils.isAopProxy(service)
      assert violated { service.save(TAKEN) } == ['advice.name-free']
      assert service.calls.isEmpty()
      service.save(FREE)
      assert service.calls == [FREE]
    }
  }

  def 'a method checks the rules it lists: #method'() {
    expect:
    runner.run { context ->
      def service = context.getBean(VehicleService)
      assert violated { service."$method"(404L) } == ['advice.vehicle-exists']
      service."$method"(1L)
      assert service.calls == [1L]
    }

    where:
    method << [
      'delete',
      'deletePrimitive',
      'deleteMeta'
    ]
  }

  def 'a method with rules sets its mode, and excludes rules bound by type'() {
    expect:
    runner.run { context ->
      def service = context.getBean(VehicleService)
      assert violated { service.save(TAKEN_AND_LONG) } == ['advice.name-free']
      assert violated {
        service.saveAll(TAKEN_AND_LONG)
      } == [
        'advice.name-free',
        'advice.name-short'
      ]
      assert violated {
        service.saveLong(new SaveVehicle('Boreas'.padRight(11, '!')))
      } == []
    }
  }

  def 'a service method that takes no rule target is not advised'() {
    expect:
    runner.run {
      assert it.getBean(VehicleService).describe('anything') == 'anything'
    }
  }

  def 'a bean that is no service checks only the methods with rules'() {
    expect:
    runner.run { context ->
      def component = context.getBean(VehicleComponent)
      component.save(TAKEN)
      assert violated { component.checked(TAKEN) } == ['advice.name-free']
      assert component.calls == [TAKEN]
    }
  }

  def 'rules declared on an interface method apply, also through a proxy based on interfaces: proxy classes #proxyClasses'() {
    expect:
    runner.withBean(OperationsService)
        .withPropertyValues("spring.aop.proxy-target-class=$proxyClasses")
        .run { context ->
          def operations = context.getBean(VehicleOperations)
          assert Proxy.isProxyClass(operations.getClass()) == !proxyClasses
          assert violated {
            operations.delete(404L)
          } == ['advice.vehicle-exists']
          operations.delete(1L)
        }

    where:
    proxyClasses << [true, false]
  }

  def 'rule targets of other classes bind on their first check, also from many threads at once'() {
    given:
    def threads = 16
    def start = new CountDownLatch(1)
    def pool = Executors.newFixedThreadPool(threads)

    expect:
    runner.run { context ->
      def service = context.getBean(VehicleService)
      def tasks = (0..<threads).collect { index -> {

          ->
          start.await()
          def wrong = []
          100.times {
            def vehicle = index % 2 == 0 ? new SaveVehicle('Atlas') : new RenameVehicle('Atlas')
            if (violated { service.update(vehicle) } != ['advice.name-free']) {
              wrong << vehicle
            }
          }
          wrong
        } as Callable<List>
      }
      def futures = tasks.collect { pool.submit(it) }
      start.countDown()
      assert futures.collectMany { it.get(1, TimeUnit.MINUTES) }.isEmpty()
      assert service.calls.isEmpty()
    }

    cleanup:
    pool.shutdownNow()
  }

  def 'a bean created after the start is advised too'() {
    expect:
    runner.withBean(LazyVehicleService).run { context ->
      def service = context.getBean(LazyVehicleService)
      assert violated { service.save(TAKEN) } == ['advice.name-free']
    }
  }

  def 'a bean registered after the start is advised too'() {
    expect:
    runner.run { context ->
      context.sourceApplicationContext.registerBean(OperationsService)
      def operations = context.getBean(VehicleOperations)
      assert violated { operations.delete(404L) } == ['advice.vehicle-exists']
    }
  }

  def 'an advised method returns what the method returns'() {
    expect:
    runner.run { assert it.getBean(VehicleService).find(7L) == 'vehicle 7' }
  }

  def 'the advice has the lowest precedence, so that the transaction advice runs around it'() {
    expect:
    runner.run {
      assert it.getBean(RulesAdvisor).order == Ordered.LOWEST_PRECEDENCE
    }
  }

  def 'the advice is named after the method in the log'() {
    given:
    def logged = []
    def log = Logger.getLogger('me.yakar.muster')
    def level = log.level
    def handler = new Handler() {
          void publish(LogRecord record) {
            logged << record.message
          }

          void flush() {}

          void close() {}
        }
    log.level = Level.ALL
    log.addHandler(handler)

    when:
    runner.run { assert it.startupFailure == null }

    then:
    logged.any { it.startsWith('Rules bound to VehicleService.delete(Long)') }

    cleanup:
    log.removeHandler(handler)
    log.level = level
  }

  def 'wrong use of rules stops the application: #bean.simpleName'() {
    expect:
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))
        .withUserConfiguration(Rules)
        .withBean(bean)
        .run { context ->
          assert MusterAutoConfigurationSpec.rootCause(context.startupFailure).message == message
        }

    where:
    bean || message
    TwoParameters || 'TwoParameters.rename(Long, SaveVehicle) takes 2 parameters; a method with rules takes one, so pass a command object that holds them, such as a record'
    TwoParametersWithRules || 'TwoParametersWithRules.rename(Long, SaveVehicle) takes 2 parameters; a method with rules takes one, so pass a command object that holds them, such as a record'
    NoParameter || 'NoParameter.deleteAll() has rules but no parameter; rules check the only parameter of a method'
    NotPublic || 'NotPublic.delete(Long) has rules but is not public; a proxy calls public methods only'
    FinalMethod || 'FinalMethod.delete(Long) has rules but is final, so a proxy cannot check them before it runs'
    StaticMethod || 'StaticMethod.delete(Long) has rules but is static, so a proxy cannot check them before it runs'
    TwoModes || '@Rules on TwoModes.save(SaveVehicle) names 2 modes; give one, or none for the mode of Muster'
  }

  def 'a listed rule that is not registered stops the application'() {
    expect:
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))
        .withUserConfiguration(Rules)
        .withBean(UnregisteredRule)
        .run { context ->
          assert MusterAutoConfigurationSpec.rootCause(context.startupFailure) instanceof IllegalArgumentException
        }
  }

  def 'the advisor is infrastructure, so that the proxy creator of transaction management applies it'() {
    expect:
    runner.run { context ->
      assert context.beanFactory.getBeanDefinition('musterRulesAdvisor').role == BeanDefinition.ROLE_INFRASTRUCTURE
      assert context.beanFactory.getBeanDefinition('musterRulesInterceptor').role == BeanDefinition.ROLE_INFRASTRUCTURE
    }
  }
}
