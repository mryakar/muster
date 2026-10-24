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

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import org.aopalliance.intercept.MethodInterceptor
import org.springframework.aop.support.DefaultPointcutAdvisor
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Role
import org.springframework.core.Ordered
import org.springframework.core.PriorityOrdered
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared
import spock.lang.Specification

/**
 * Measures how an advice at the lowest precedence, where the starter's advice will sit, orders
 * against Spring's transaction advice: whether it runs inside the transaction, and what decides it.
 */
class TransactionOrderSpec extends Specification {

  @Shared
  PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))

  def setupSpec() {
    postgres.start()
  }

  def cleanupSpec() {
    postgres.stop()
  }

  def "with equal orders, the advisor registered first runs outside the transaction"() {
    expect:
    probe(ProbeFromConfiguration) == [false]
  }

  def "with equal orders, the advisor registered after the transaction advisor runs inside it"() {
    expect:
    probe(ProbeAfterAutoConfiguration) == [true]
  }

  def "moving the transaction advisor up one step puts the advice inside, wherever it is registered"() {
    expect:
    probe(ProbeFromConfiguration, TransactionAdvisorOneStepUp) == [true]
    probe(ProbeAfterAutoConfiguration, TransactionAdvisorOneStepUp) == [true]
  }

  def "the transaction advisor moves before a service that a bean post-processor pulls in early"() {
    expect:
    probe(ProbeFromConfiguration, TransactionAdvisorOneStepUp, EarlyService) == [true]
  }

  def "an order the user gives puts the transaction outside the advice"() {
    expect:
    probe(ProbeFromConfiguration, UserTransactionOrder) == [true]
  }

  /** Calls the probed service once and returns whether the advice saw an active transaction. */
  private List<Boolean> probe(Class<?>... sources) {
    ConfigurableApplicationContext context = new SpringApplicationBuilder(([Application] + sources.toList()) as Class<?>[])
    .web(WebApplicationType.NONE)
    .properties(
    "spring.datasource.url=${postgres.jdbcUrl}",
    "spring.datasource.username=${postgres.username}",
    "spring.datasource.password=${postgres.password}")
    .run()
    try {
      def service = context.getBean(ProbedService)
      service.call()
      assert service.inTransaction == [true]
      context.getBean(Probe).inTransaction
    } finally {
      context.close()
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  static @interface Probed {}

  static class ProbedService {
    List<Boolean> inTransaction = []

    @Transactional
    @Probed
    void call() {
      inTransaction << TransactionSynchronizationManager.actualTransactionActive
    }
  }

  /** An advisor at the lowest precedence on {@link Probed} methods, as the starter's will be. */
  static class Probe extends DefaultPointcutAdvisor {
    List<Boolean> inTransaction = []

    Probe() {
      super(AnnotationMatchingPointcut.forMethodAnnotation(Probed), null)
      setAdvice({ invocation ->
        inTransaction << TransactionSynchronizationManager.actualTransactionActive
        invocation.proceed()
      } as MethodInterceptor)
      setOrder(Ordered.LOWEST_PRECEDENCE)
    }
  }

  @EnableAutoConfiguration
  static class Application {
    @Bean
    ProbedService probedService() {
      new ProbedService()
    }
  }

  /** Registers the probe from the user's configuration, before the auto-configurations. */
  static class ProbeFromConfiguration {
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    Probe probe() {
      new Probe()
    }
  }

  /** Registers the probe after every configuration class, the transaction advisor among them. */
  static class ProbeAfterAutoConfiguration {
    @Bean
    static BeanDefinitionRegistryPostProcessor registerProbe() {
      { BeanDefinitionRegistry registry ->
        def probe = new RootBeanDefinition(Probe)
        probe.role = BeanDefinition.ROLE_INFRASTRUCTURE
        registry.registerBeanDefinition("probe", probe)
      } as BeanDefinitionRegistryPostProcessor
    }
  }

  /**
   * The mechanism to prove: a transaction advisor left at the default order moves up one step. The
   * factory method declares the post-processor's own type: Spring reads PriorityOrdered from the
   * declared type before it creates the bean, and with BeanPostProcessor declared it registers the
   * post-processor last, after the transaction advisor is created.
   */
  static class TransactionAdvisorOneStepUp {
    @Bean
    static OneStepUp transactionAdvisorOneStepUp() {
      new OneStepUp()
    }
  }

  static class OneStepUp implements BeanPostProcessor, PriorityOrdered {
    @Override
    Object postProcessAfterInitialization(Object bean, String beanName) {
      if (bean instanceof BeanFactoryTransactionAttributeSourceAdvisor
          && bean.order == Ordered.LOWEST_PRECEDENCE) {
        bean.order = Ordered.LOWEST_PRECEDENCE - 1
      }
      bean
    }

    @Override
    int getOrder() {
      Ordered.HIGHEST_PRECEDENCE
    }
  }

  /** A post-processor of the user that needs the service, so the service is created early. */
  static class EarlyService {
    @Bean
    static BeanPostProcessor needsService(ProbedService service) {
      new BeanPostProcessor() {}
    }
  }

  @EnableTransactionManagement(order = 0)
  static class UserTransactionOrder {}
}
