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

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause

import me.yakar.muster.spring.MusterAutoConfiguration
import me.yakar.muster.spring.fixture.transaction.FakeTransactionManager
import me.yakar.muster.spring.fixture.transaction.TransactionProbe
import me.yakar.muster.spring.fixture.transaction.TransactionalService
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.core.Ordered
import org.springframework.core.PriorityOrdered
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor
import org.springframework.transaction.interceptor.TransactionAttributeSource
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import spock.lang.Specification

class TransactionAdviceSpec extends Specification {

  @Configuration(proxyBeanMethods = false)
  @AutoConfigurationPackage(basePackageClasses = TransactionProbe)
  static class Application {
    @Bean
    TransactionalService service() {
      new TransactionalService()
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement
  static class Transactions {
    @Bean
    PlatformTransactionManager transactionManager() {
      new FakeTransactionManager()
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement
  static class TransactionsWithoutSynchronization {
    @Bean
    PlatformTransactionManager transactionManager() {
      new FakeTransactionManager(AbstractPlatformTransactionManager.SYNCHRONIZATION_NEVER)
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement(order = 0)
  static class TransactionsWithOrder {
    @Bean
    PlatformTransactionManager transactionManager() {
      new FakeTransactionManager()
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class SecondAttributeSource {
    @Bean
    TransactionAttributeSource secondAttributeSource() {
      new AnnotationTransactionAttributeSource()
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement
  @AutoConfigurationPackage(basePackageClasses = TransactionProbe)
  static class LazyTransactionalService {
    @Bean
    PlatformTransactionManager transactionManager() {
      new FakeTransactionManager()
    }

    @Bean
    @Lazy
    TransactionalService lazyService() {
      new TransactionalService()
    }
  }

  /** Sets an order on the definition of the transaction advisor, as XML configuration does. */
  @Configuration(proxyBeanMethods = false)
  static class OrderOnDefinition {
    @Bean
    static BeanFactoryPostProcessor orderOnDefinition() {
      new OrderOnDefinitionPostProcessor()
    }
  }

  static class OrderOnDefinitionPostProcessor implements BeanFactoryPostProcessor, PriorityOrdered {
    void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
      beanFactory.getBeanDefinition('org.springframework.transaction.config.internalTransactionAdvisor').propertyValues.add('order', 100)
    }

    int getOrder() {
      Ordered.HIGHEST_PRECEDENCE
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement(order = 0)
  static class SecondTransactionManagement {}

  def runner = new ApplicationContextRunner()
  .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))
  .withUserConfiguration(Application)

  def setup() {
    TransactionProbe.IN_TRANSACTION.clear()
  }

  def 'rules run inside the transaction, which moved one step ahead: #method'() {
    expect:
    runner.withUserConfiguration(Transactions).run { context ->
      context.getBean(TransactionalService)."$method"('call')
      assert TransactionProbe.IN_TRANSACTION == [inside]
      assert context.getBean(BeanFactoryTransactionAttributeSourceAdvisor).order == Ordered.LOWEST_PRECEDENCE - 1
    }

    where:
    method || inside
    'required' || true
    'supports' || false
    'plain' || false
  }

  def 'a method that requires a transaction fails when its rules run with none active'() {
    when:
    runner.withUserConfiguration(TransactionsWithoutSynchronization).run { context ->
      def service = context.getBean(TransactionalService)
      service.supports('call')
      service.required('call')
    }

    then: 'only the rule of the method that supports a transaction ran'
    def e = thrown(IllegalStateException)
    e.message == 'TransactionalService.required(String) runs in a transaction, but its rules ran with none active; the transaction advice must run around Muster\'s'
    TransactionProbe.IN_TRANSACTION == [false]
  }

  def 'without transaction management nothing is guarded'() {
    expect:
    runner.run { context ->
      context.getBean(TransactionalService).required('call')
      assert TransactionProbe.IN_TRANSACTION == [false]
    }
  }

  def 'the guard reads the attribute source of the transaction advice, whatever other sources exist'() {
    when:
    runner.withUserConfiguration(TransactionsWithoutSynchronization, SecondAttributeSource).run { context ->
      context.getBean(TransactionalService).required('call')
    }

    then:
    def e = thrown(IllegalStateException)
    e.message.startsWith('TransactionalService.required(String) runs in a transaction')
  }

  def 'without Spring transactions on the class path, rules still run'() {
    expect:
    runner.withClassLoader(new FilteredClassLoader('org.springframework.transaction')).run { context ->
      assert !context.containsBean('musterTransactionGuard')
      context.getBean(TransactionalService).plain('call')
      assert TransactionProbe.IN_TRANSACTION == [false]
    }
  }

  def 'transactions woven in with AspectJ stop the application'() {
    expect:
    runner.withInitializer {
      it.registerBeanDefinition('org.springframework.transaction.config.internalTransactionAspect', new RootBeanDefinition(Object))
    }.run { context ->
      assert getMostSpecificCause(context.startupFailure).message ==
      'transactions are woven in with AspectJ, so they cannot run around Muster\'s advice, and rules would run outside them; use @EnableTransactionManagement in proxy mode'
    }
  }

  def 'an order the application gives the transaction advice is left alone'() {
    expect:
    runner.withUserConfiguration(TransactionsWithOrder).run { context ->
      assert context.getBean(BeanFactoryTransactionAttributeSourceAdvisor).order == 0
      context.getBean(TransactionalService).required('call')
      assert TransactionProbe.IN_TRANSACTION == [true]
    }
  }

  def 'a bean created after the start checks the order of the transaction advice when it is created'() {
    when:
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))
        .withUserConfiguration(LazyTransactionalService)
        .withPropertyValues('muster.transaction.reorder-advisor=false')
        .run { context ->
          assert context.startupFailure == null
          context.getBean('lazyService')
        }

    then:
    def e = thrown(Exception)
    getMostSpecificCause(e).message.startsWith('the transaction advice has the order')
  }

  def 'an order set on the definition of the transaction advisor is left alone'() {
    expect:
    runner.withUserConfiguration(Transactions, OrderOnDefinition).run { context ->
      assert context.getBean(BeanFactoryTransactionAttributeSourceAdvisor).order == 100
    }
  }

  def 'an order that one of several @EnableTransactionManagement gives is left alone'() {
    expect:
    runner.withUserConfiguration(Transactions, SecondTransactionManagement).run { context ->
      assert context.getBean(BeanFactoryTransactionAttributeSourceAdvisor).order != Ordered.LOWEST_PRECEDENCE - 1
    }
  }

  def 'the order check names how to fix it'() {
    expect:
    runner.withUserConfiguration(Transactions).withPropertyValues('muster.transaction.reorder-advisor=false').run { context ->
      assert getMostSpecificCause(context.startupFailure).message ==
      "the transaction advice has the order ${Ordered.LOWEST_PRECEDENCE}, which does not come before Muster's, ${Ordered.LOWEST_PRECEDENCE}, so rules could run outside the transaction; let Muster move it, with the order of @EnableTransactionManagement at its default and muster.transaction.reorder-advisor on, or give it a lower order"
    }
  }

  def 'an application without advised methods starts even if the transaction advice comes after Muster'() {
    expect:
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))
        .withUserConfiguration(Transactions)
        .withPropertyValues('muster.transaction.reorder-advisor=false')
        .run { context -> assert context.startupFailure == null }
  }
}
