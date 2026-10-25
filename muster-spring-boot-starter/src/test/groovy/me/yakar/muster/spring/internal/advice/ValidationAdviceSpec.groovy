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

import jakarta.validation.ConstraintViolationException
import me.yakar.muster.spring.MusterAutoConfiguration
import me.yakar.muster.spring.fixture.transaction.FakeTransactionManager
import me.yakar.muster.spring.fixture.validation.Steps
import me.yakar.muster.spring.fixture.validation.ValidatedService
import me.yakar.muster.spring.fixture.validation.ValidationProbe
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.PriorityOrdered
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor
import spock.lang.Specification

class ValidationAdviceSpec extends Specification {

  @Configuration(proxyBeanMethods = false)
  @AutoConfigurationPackage(basePackageClasses = ValidationProbe)
  static class Application {
    @Bean
    ValidatedService service() {
      new ValidatedService()
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

  /** Defines the post-processor itself, so Spring Boot does not. */
  @Configuration(proxyBeanMethods = false)
  static class OwnPostProcessor {
    @Bean
    static MethodValidationPostProcessor methodValidationPostProcessor() {
      new MethodValidationPostProcessor()
    }
  }

  /** Sets the property on the definition of the post-processor, as XML configuration does. */
  @Configuration(proxyBeanMethods = false)
  static class PropertyOnDefinition {
    @Bean
    static BeanFactoryPostProcessor propertyOnDefinition() {
      new PropertyOnDefinitionPostProcessor()
    }
  }

  static class PropertyOnDefinitionPostProcessor implements BeanFactoryPostProcessor, PriorityOrdered {
    void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
      beanFactory.getBeanDefinition(ValidationOrdering.POST_PROCESSOR).propertyValues.add('beforeExistingAdvisors', false)
    }

    int getOrder() {
      Ordered.HIGHEST_PRECEDENCE
    }
  }

  def runner = new ApplicationContextRunner()
  .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration, ValidationAutoConfiguration))
  .withUserConfiguration(Application)

  def setup() {
    Steps.NOTED.clear()
  }

  def 'the constraints of a method are checked before its rules'() {
    expect:
    runner.run { context ->
      context.getBean(ValidatedService).create('Atlas')
      assert Steps.NOTED == ['constraint', 'rule']
    }
  }

  def 'an input that violates a constraint reaches no rule'() {
    when:
    runner.run { context ->
      context.getBean(ValidatedService).create(' ')
    }

    then:
    thrown(ConstraintViolationException)
    Steps.NOTED == ['constraint']
  }

  def 'the constraints are checked before the transaction of the method'() {
    expect:
    runner.withUserConfiguration(Transactions).run { context ->
      context.getBean(ValidatedService).createInTransaction('Atlas')
      assert Steps.NOTED == ['constraint', 'rule in transaction']
    }
  }

  def 'with muster.validation.reorder-advisor off, Spring runs the rules first'() {
    when:
    runner.withPropertyValues('muster.validation.reorder-advisor=false').run { context ->
      context.getBean(ValidatedService).create(' ')
    }

    then:
    thrown(ConstraintViolationException)
    Steps.NOTED == ['rule', 'constraint']
  }

  def 'a post-processor the application defines is left alone: #how'() {
    when:
    configure(runner).run { context ->
      context.getBean(ValidatedService).create(' ')
    }

    then:
    thrown(ConstraintViolationException)
    Steps.NOTED == ['rule', 'constraint']

    where:
    how || configure
    'from a bean method' || { ApplicationContextRunner r ->
      r.withUserConfiguration(OwnPostProcessor)
    }
    'registered as a class' || { ApplicationContextRunner r ->
      r.withInitializer {
        it.registerBeanDefinition(ValidationOrdering.POST_PROCESSOR, new AnnotatedGenericBeanDefinition(MethodValidationPostProcessor))
      }
    }
    'registered as a definition' || { ApplicationContextRunner r ->
      r.withInitializer {
        it.registerBeanDefinition(ValidationOrdering.POST_PROCESSOR, new RootBeanDefinition(MethodValidationPostProcessor))
      }
    }
  }

  def 'a property set on the definition of the post-processor is left alone'() {
    when:
    runner.withUserConfiguration(PropertyOnDefinition).run { context ->
      context.getBean(ValidatedService).create(' ')
    }

    then:
    thrown(ConstraintViolationException)
    Steps.NOTED == ['rule', 'constraint']
  }

  def 'without method validation, rules run as before'() {
    expect:
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))
        .withUserConfiguration(Application)
        .run { context ->
          context.getBean(ValidatedService).create(' ')
          assert Steps.NOTED == ['rule']
        }
  }
}
