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

import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import me.yakar.muster.BusinessRuleViolationException
import me.yakar.muster.Muster
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.spring.fixture.app.NameCapitalized
import me.yakar.muster.spring.fixture.app.NameShort
import me.yakar.muster.spring.fixture.app.NameUnique
import me.yakar.muster.spring.fixture.app.Names
import me.yakar.muster.spring.fixture.app.Vehicle
import me.yakar.muster.spring.fixture.other.OtherRule
import me.yakar.muster.spring.fixture.prototype.PrototypeRule
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Scope
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import spock.lang.Specification

class MusterAutoConfigurationSpec extends Specification {

  @Configuration(proxyBeanMethods = false)
  @AutoConfigurationPackage(basePackageClasses = NameUnique)
  static class Application {
    @Bean
    Names names() {
      new Names(['Atlas'] as Set)
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class RuleFromBeanMethod {
    @Bean
    NameShort shortName() {
      new NameShort()
    }
  }

  @Configuration(proxyBeanMethods = false)
  @Import(NameCapitalized)
  static class ImportedRule {}

  @Configuration(proxyBeanMethods = false)
  static class OtherRuleAsBean {
    @Bean
    OtherRule otherRule() {
      new OtherRule()
    }
  }

  @Configuration(proxyBeanMethods = false)
  @AutoConfigurationPackage(basePackageClasses = PrototypeRule)
  static class PrototypePackage {}

  @Configuration(proxyBeanMethods = false)
  static class PrototypeBean {
    @Bean
    @Scope('prototype')
    OtherRule otherRule() {
      new OtherRule()
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class RuleDeclaredAsInterface {
    @Bean
    BusinessRule<Object> vague() {
      new OtherRule()
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class UnannotatedRule {
    @Bean
    me.yakar.muster.spring.fixture.app.Unannotated unannotated() {
      new me.yakar.muster.spring.fixture.app.Unannotated()
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class RuleFromFactoryBean {
    @Bean
    FactoryBean<OtherRule> otherRule() {
      new FactoryBean<OtherRule>() {
            OtherRule getObject() {
              new OtherRule()
            }

            Class<?> getObjectType() {
              OtherRule
            }
          }
    }
  }

  @Configuration(proxyBeanMethods = false)
  @Import(UnknownFactory)
  static class BeanOfUnknownType {
    static boolean postProcessed
    static boolean createdEarly

    /** Runs after every registry post-processor, the rule scanner included. */
    @Bean
    static BeanFactoryPostProcessor marker() {
      { beanFactory -> postProcessed = true } as BeanFactoryPostProcessor
    }
  }

  /** A factory bean whose product Spring knows only by creating it. */
  @SuppressWarnings('rawtypes')
  static class UnknownFactory implements FactoryBean {
    UnknownFactory() {
      BeanOfUnknownType.createdEarly |= !BeanOfUnknownType.postProcessed
    }

    Object getObject() {
      'not a rule'
    }

    Class<?> getObjectType() {
      null
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class OwnMuster {
    @Bean
    Muster muster() {
      Muster.builder().rules(new OtherRule()).build()
    }
  }

  def runner = new ApplicationContextRunner()
  .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))

  def 'registers the rule classes of the application packages as beans, named after their class'() {
    expect:
    runner.withUserConfiguration(Application).run { context ->
      assert context.getBeanNamesForType(BusinessRule) as Set == [
        NameCapitalized,
        NameShort,
        NameUnique
      ]*.name as Set
      assert context.getBean(NameUnique).names.is(context.getBean(Names))
    }
  }

  def 'Muster checks the rules it found'() {
    expect:
    runner.withUserConfiguration(Application).run { context ->
      def muster = context.getBean(Muster)
      muster.check(new Vehicle('Boreas'))
      try {
        muster.check(new Vehicle('Atlas'))
        assert false: 'the taken name passed'
      } catch (BusinessRuleViolationException e) {
        assert e.violations()*.code() == ['vehicle.name-unique']
      }
    }
  }

  def 'a rule class that is already a bean is not registered again: #configuration.simpleName'() {
    expect:
    runner.withUserConfiguration(Application, configuration).run { context ->
      assert context.getBeanNamesForType(ruleClass) as List == [beanName]
      assert context.getBean(Muster).chain(Vehicle, ruleClass) != null
    }

    where:
    configuration      | ruleClass || beanName
    RuleFromBeanMethod | NameShort || 'shortName'
    ImportedRule       | NameCapitalized || NameCapitalized.name
  }

  def 'scans through the resource loader of the application context'() {
    given:
    def patterns = []
    def resolver = new PathMatchingResourcePatternResolver() {
          Resource[] getResources(String pattern) {
            patterns << pattern
            super.getResources(pattern)
          }
        }

    expect:
    runner.withUserConfiguration(Application)
        .withInitializer { it.resourceLoader = resolver }
        .run { assert it.getBean(Muster).chain(Vehicle, NameUnique) != null }
    patterns.contains('classpath*:me/yakar/muster/spring/fixture/app/**/*.class')
  }

  def 'the scan neither creates a factory bean early nor stops at one whose type is not known'() {
    given:
    BeanOfUnknownType.postProcessed = false
    BeanOfUnknownType.createdEarly = false

    expect:
    runner.withUserConfiguration(Application, BeanOfUnknownType).run { context ->
      assert context.getBean(Muster).chain(Vehicle, NameUnique) != null
      assert !BeanOfUnknownType.createdEarly
    }
  }

  def 'a rule registered as a singleton is not registered again by the scan'() {
    given:
    def mine = new NameShort()

    expect:
    runner.withUserConfiguration(Application)
        .withInitializer { it.beanFactory.registerSingleton('mine', mine) }
        .run { context ->
          assert context.getBeanNamesForType(NameShort) as List == ['mine']
          assert context.getBean(Muster).chain(Vehicle, NameShort) != null
        }
  }

  def 'an abstract bean definition whose class is missing does not stop the scan'() {
    given:
    def template = new GenericBeanDefinition(beanClassName: 'com.example.Missing', abstract: true)

    expect:
    runner.withUserConfiguration(Application)
        .withInitializer { it.registerBeanDefinition('template', template) }
        .run { context -> assert context.startupFailure == null }
  }

  def 'each registered rule is logged at DEBUG'() {
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
    runner.withUserConfiguration(Application).run {
      assert it.startupFailure == null
    }

    then:
    logged.findAll {
      it.startsWith('Registered the rule')
    } as Set == [
      NameCapitalized,
      NameShort,
      NameUnique
    ].collect {
      "Registered the rule ${it.name} as a bean".toString()
    } as Set

    cleanup:
    log.removeHandler(handler)
    log.level = level
  }

  def 'a rule outside the application packages joins as a bean'() {
    expect:
    runner.withUserConfiguration(Application, OtherRuleAsBean).run { context ->
      def muster = context.getBean(Muster)
      try {
        muster.chain(Object, OtherRule).check('anything')
        assert false: 'the rule did not run'
      } catch (BusinessRuleViolationException e) {
        assert e.violations()*.code() == ['other.rule']
      }
    }
  }

  def 'without application packages nothing is scanned'() {
    expect:
    runner.withUserConfiguration(OtherRuleAsBean).run { context ->
      assert context.getBeanNamesForType(BusinessRule) as List == ['otherRule']
    }
  }

  def 'a rule that a factory bean makes joins'() {
    expect:
    runner.withUserConfiguration(RuleFromFactoryBean).run { context ->
      assert context.getBean(Muster).chain(Object, OtherRule) != null
    }
  }

  def 'a rule registered as a singleton without a bean definition joins'() {
    expect:
    runner.withInitializer {
      it.beanFactory.registerSingleton('registered', new OtherRule())
    }.run { context ->
      assert context.getBean(Muster).chain(Object, OtherRule) != null
    }
  }

  def 'a rule bean that is not a singleton is rejected: #configuration.simpleName'() {
    expect:
    runner.withUserConfiguration(configuration).run { context ->
      assert context.startupFailure != null
      assert rootCause(context.startupFailure).message ==
      "the rule bean '$name' has the scope 'prototype'; Muster creates each rule once and keeps it, so a rule bean must be a singleton"
    }

    where:
    configuration || name
    PrototypePackage || PrototypeRule.name
    PrototypeBean || 'otherRule'
  }

  def 'a rule bean whose type has no @Rule is rejected: #configuration.simpleName'() {
    // The bean 'vague' is created before Muster, so its class is known by then; the outcome must
    // not depend on that.
    expect:
    runner.withUserConfiguration(configuration).run { context ->
      assert rootCause(context.startupFailure).message == message
    }

    where:
    configuration || message
    RuleDeclaredAsInterface || "the rule bean 'vague' is declared as me.yakar.muster.rule.BusinessRule, which has no @Rule; declare the rule's own class as the type of its @Bean method"
    UnannotatedRule || "the rule bean 'unannotated' is a me.yakar.muster.spring.fixture.app.Unannotated, which has no @Rule; give the rule an id with @Rule"
  }

  def 'a Muster of the application replaces the one built here'() {
    expect:
    runner.withUserConfiguration(Application, OwnMuster).run { context ->
      assert context.getBeansOfType(Muster).keySet() == ['muster'] as Set
      context.getBean(Muster).chain(Object, OtherRule)
      try {
        context.getBean(Muster).chain(Vehicle, NameUnique)
        assert false: 'the scanned rule is registered'
      } catch (IllegalArgumentException expected) {
      }
    }
  }

  static Throwable rootCause(Throwable failure) {
    def cause = failure
    while (cause.cause != null) {
      cause = cause.cause
    }
    cause
  }
}
