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
import me.yakar.muster.spring.fixture.validation.EarlyAspect
import me.yakar.muster.spring.fixture.validation.Steps
import me.yakar.muster.spring.fixture.validation.ValidatedOnlyService
import me.yakar.muster.spring.fixture.validation.ValidatedService
import me.yakar.muster.spring.fixture.validation.ValidationProbe
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Role
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

  /** Puts the validation advice ahead of all other advice itself. */
  @Configuration(proxyBeanMethods = false)
  static class OwnPostProcessorBeforeExisting {
    @Bean
    static MethodValidationPostProcessor methodValidationPostProcessor() {
      new MethodValidationPostProcessor(beforeExistingAdvisors: true)
    }
  }

  /** Advice ordered before the transaction's and Muster's; infrastructure, as method security is. */
  @Configuration(proxyBeanMethods = false)
  static class WithEarlyAspect {
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static EarlyAspect earlyAspect() {
      new EarlyAspect()
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class WithoutRules {
    @Bean
    ValidatedOnlyService validatedOnly() {
      new ValidatedOnlyService()
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

  def 'the validation advice of a post-processor the application defines is moved as well'() {
    expect:
    runner.withUserConfiguration(OwnPostProcessor).run { context ->
      context.getBean(ValidatedService).create('Atlas')
      assert Steps.NOTED == ['constraint', 'rule']
    }
  }

  def 'other advice keeps its place: the validation advice moves only ahead of the rules'() {
    expect:
    runner.withUserConfiguration(Transactions, WithEarlyAspect).run { context ->
      context.getBean(ValidatedService).createInTransaction('Atlas')
      assert Steps.NOTED == ['aspect', 'constraint', 'rule in transaction']
    }
  }

  def 'a validation advice that already comes before the rules is left where it is'() {
    expect:
    runner.withUserConfiguration(Transactions, WithEarlyAspect, OwnPostProcessorBeforeExisting).run { context ->
      context.getBean(ValidatedService).createInTransaction('Atlas')
      assert Steps.NOTED == ['constraint', 'aspect', 'rule in transaction']
    }
  }

  def 'a validated bean without rules keeps the order Spring gives it'() {
    expect:
    runner.withUserConfiguration(WithEarlyAspect, WithoutRules).run { context ->
      context.getBean(ValidatedOnlyService).create('Atlas')
      assert Steps.NOTED == ['aspect', 'constraint']
    }
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
