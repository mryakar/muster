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

import java.lang.reflect.Proxy
import me.yakar.muster.BusinessRuleViolationException
import me.yakar.muster.spring.MusterAutoConfiguration
import me.yakar.muster.spring.fixture.advice.BaseFinder
import me.yakar.muster.spring.fixture.advice.BaseSaver
import me.yakar.muster.spring.fixture.advice.GenericOperations
import me.yakar.muster.spring.fixture.advice.GenericOperationsImpl
import me.yakar.muster.spring.fixture.advice.InheritingSaver
import me.yakar.muster.spring.fixture.advice.NameFree
import me.yakar.muster.spring.fixture.advice.NotAServiceItself
import me.yakar.muster.spring.fixture.advice.PlainOperations
import me.yakar.muster.spring.fixture.advice.PlainOperationsImpl
import me.yakar.muster.spring.fixture.advice.SaveVehicle
import me.yakar.muster.spring.fixture.advice.StaticCalls
import me.yakar.muster.spring.fixture.advice.StaticSaver
import me.yakar.muster.spring.fixture.advice.SubFinder
import me.yakar.muster.spring.fixture.advice.SubSaver
import me.yakar.muster.spring.fixture.advice.TargetOperations
import me.yakar.muster.spring.fixture.advice.TargetService
import me.yakar.muster.spring.fixture.advice.VehicleOperations
import org.springframework.aop.framework.ProxyFactory
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import spock.lang.Specification

/** Rules apply however a method is reached: through an interface, a bridge or a base class. */
class HierarchyAdviceSpec extends Specification {

  static final SaveVehicle TAKEN = new SaveVehicle('Atlas')

  @Configuration(proxyBeanMethods = false)
  @AutoConfigurationPackage(basePackageClasses = NameFree)
  static class Rules {}

  /** A bean that is a proxy based on interfaces itself, as a Spring Data repository is. */
  @Configuration(proxyBeanMethods = false)
  static class InterfaceProxyBean {
    @Bean
    VehicleOperations repository() {
      def calls = []
      def factory = new ProxyFactory()
      factory.addInterface(VehicleOperations)
      factory.addAdvice({ invocation ->
        calls << invocation.arguments[0]; null
      } as org.aopalliance.intercept.MethodInterceptor)
      factory.proxy as VehicleOperations
    }
  }

  def runner = new ApplicationContextRunner()
  .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))
  .withUserConfiguration(Rules)

  static List<String> violated(Closure call) {
    try {
      call()
      []
    } catch (BusinessRuleViolationException e) {
      e.violations()*.code()
    }
  }

  def 'rules apply through #through, proxy classes #proxyClasses'() {
    expect:
    runner.withBean(bean).withPropertyValues("spring.aop.proxy-target-class=$proxyClasses").run { context ->
      assert context.startupFailure == null
      def target = context.getBean(type)
      assert violated { call(target) } == codes
    }

    where:
    through | bean | type | call | proxyClasses || codes
    'a generic interface' | GenericOperationsImpl | GenericOperations | {
      StaticCalls.deleteGeneric(it, 404L)
    } | true || ['advice.vehicle-exists']
    'a generic interface' | GenericOperationsImpl | GenericOperations | {
      StaticCalls.deleteGeneric(it, 404L)
    } | false || ['advice.vehicle-exists']
    'an interface without rules' | PlainOperationsImpl | PlainOperations | {
      StaticCalls.deletePlain(it, 404L)
    } | true || ['advice.vehicle-exists']
    'an interface without rules' | PlainOperationsImpl | PlainOperations | {
      StaticCalls.deletePlain(it, 404L)
    } | false || ['advice.vehicle-exists']
    'a generic interface for rule targets' | TargetService | TargetOperations | {
      StaticCalls.saveTarget(it, TAKEN)
    } | true || ['advice.name-free']
    'a generic interface for rule targets' | TargetService | TargetOperations | {
      StaticCalls.saveTarget(it, TAKEN)
    } | false || ['advice.name-free']
    'a generic base class' | SubSaver | BaseSaver | {
      StaticCalls.saveBase(it, TAKEN)
    } | true || ['advice.name-free']
    'a generic base class' | SubSaver | BaseSaver | {
      StaticCalls.saveBase(it, TAKEN)
    } | false || ['advice.name-free']
    'a method with a narrower return type' | SubFinder | BaseFinder | {
      StaticCalls.findBase(it, 404L)
    } | true || ['advice.vehicle-exists']
    'a method with a narrower return type' | SubFinder | BaseFinder | {
      StaticCalls.findBase(it, 404L)
    } | false || ['advice.vehicle-exists']
  }

  def 'rules on the interface of a bean that is an interface proxy itself apply'() {
    expect:
    runner.withUserConfiguration(InterfaceProxyBean).run { context ->
      assert context.startupFailure == null
      def repository = context.getBean(VehicleOperations)
      assert Proxy.isProxyClass(repository.getClass())
      assert violated {
        StaticCalls.deleteOperations(repository, 404L)
      } == ['advice.vehicle-exists']
    }
  }

  def 'a method is named after the class of the bean that runs it'() {
    given:
    def logged = []
    def log = java.util.logging.Logger.getLogger('me.yakar.muster')
    def level = log.level
    def handler = new java.util.logging.Handler() {
          void publish(java.util.logging.LogRecord record) {
            logged << record.message
          }

          void flush() {}

          void close() {}
        }
    log.level = java.util.logging.Level.ALL
    log.addHandler(handler)

    when:
    runner.withBean(SubSaver).withBean(InheritingSaver).withUserConfiguration(InterfaceProxyBean).run {
      assert it.startupFailure == null
    }

    then:
    logged.any { it.startsWith('Rules bound to SubSaver.save(SaveVehicle)') }
    logged.any {
      it.startsWith('Rules bound to InheritingSaver.save(SaveVehicle)')
    }

    and: 'a proxy based on interfaces is named after its interface'
    logged.any {
      it.startsWith('Rules bound to VehicleOperations.delete(Long)')
    }

    cleanup:
    log.removeHandler(handler)
    log.level = level
  }

  def 'a subclass of a service is no service itself, and a static method is not advised'() {
    expect:
    runner.withBean(NotAServiceItself).withBean(StaticSaver).run { context ->
      context.getBean(NotAServiceItself).save(TAKEN)
      StaticSaver.save(TAKEN)
      assert context.getBean(NotAServiceItself).calls == [TAKEN]
    }
  }
}
