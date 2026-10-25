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

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause

import me.yakar.muster.BusinessRuleViolationException
import me.yakar.muster.Muster
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.spring.fixture.annotated.AnnotatedRule
import me.yakar.muster.spring.fixture.app.NameCapitalized
import me.yakar.muster.spring.fixture.app.NameShort
import me.yakar.muster.spring.fixture.app.NameUnique
import me.yakar.muster.spring.fixture.app.Names
import me.yakar.muster.spring.fixture.app.Vehicle
import me.yakar.muster.spring.fixture.conditional.OnBean
import me.yakar.muster.spring.fixture.conditional.OnProperty
import me.yakar.muster.spring.fixture.dependson.DependsOnMissing
import me.yakar.muster.spring.fixture.other.OtherRule
import me.yakar.muster.spring.fixture.prototype.PrototypeRule
import org.springframework.aop.framework.ProxyFactory
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.BeanPostProcessor
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
  static class FactoryOfNewRules {
    @Bean
    FactoryBean<OtherRule> otherRule() {
      new FactoryBean<OtherRule>() {
            OtherRule getObject() {
              new OtherRule()
            }

            Class<?> getObjectType() {
              OtherRule
            }

            boolean isSingleton() {
              false
            }
          }
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
  @AutoConfigurationPackage(basePackageClasses = AnnotatedRule)
  static class AnnotatedPackage {
    @Bean
    Names names() {
      new Names([] as Set)
    }
  }

  @Configuration(proxyBeanMethods = false)
  @AutoConfigurationPackage(basePackageClasses = OnProperty)
  static class ConditionalPackage {}

  @Configuration(proxyBeanMethods = false)
  @AutoConfigurationPackage(basePackageClasses = DependsOnMissing)
  static class DependsOnPackage {}

  @Configuration(proxyBeanMethods = false)
  static class SameRuleTwice {
    @Bean
    OtherRule first() {
      new OtherRule()
    }

    @Bean
    OtherRule second() {
      new OtherRule()
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class RuleBehindInterfaceProxy {
    @Bean
    OtherRule otherRule() {
      new OtherRule()
    }

    /** Wraps the rule in a proxy based on its interfaces, as Spring does without class proxies. */
    @Bean
    static BeanPostProcessor interfaceProxies() {
      new BeanPostProcessor() {
            Object postProcessAfterInitialization(Object bean, String name) {
              bean instanceof OtherRule ? new ProxyFactory(bean).proxy : bean
            }
          }
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
      assert context.getBeanNamesForType(BusinessRule) as Set == [NameCapitalized, NameShort, NameUnique]*.name as Set
      assert context.getBean(NameUnique).names.is(context.getBean(Names))
    }
  }

  def 'Muster checks the rules it found'() {
    when:
    runner.withUserConfiguration(Application).run { context ->
      def muster = context.getBean(Muster)
      muster.check(new Vehicle('Boreas'))
      muster.check(new Vehicle('Atlas'))
    }

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.code() == ['vehicle.name-unique']
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

  def 'a rule registered as a CGLIB proxy counts as its rule class: scanned too: #scanned'() {
    given:
    def factory = new ProxyFactory(new NameShort())
    factory.proxyTargetClass = true
    Object proxy = factory.proxy
    assert NameShort.isInstance(proxy) && proxy.getClass() != NameShort

    expect:
    runner.withUserConfiguration(scanned ? [Application] as Class[] : [] as Class[])
    .withInitializer { it.beanFactory.registerSingleton('proxied', proxy) }
    .run { context ->
      assert context.getBeanNamesForType(NameShort) as List == ['proxied']
      assert context.getBean(Muster).chain(Vehicle, NameShort) != null
    }

    where:
    scanned << [true, false]
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
    def logs = new LogCapture()

    when:
    runner.withUserConfiguration(Application).run {
      assert it.startupFailure == null
    }

    then:
    logs.messages.findAll {
      it.startsWith('Registered the rule')
    } as Set == [NameCapitalized, NameShort, NameUnique].collect {
      "Registered the rule ${it.name} as a bean".toString()
    } as Set

    cleanup:
    logs.close()
  }

  def 'the annotations of a scanned rule apply as they do to a component'() {
    expect:
    runner.withUserConfiguration(AnnotatedPackage).run { context ->
      def definition = context.beanFactory.getBeanDefinition(AnnotatedRule.name)
      assert definition.lazyInit
      assert definition.primary
      assert definition.dependsOn == ['names'] as String[]
    }
  }

  def 'a scanned rule that depends on a missing bean stops the application, as a component would'() {
    expect:
    runner.withUserConfiguration(DependsOnPackage).run { context ->
      assert getMostSpecificCause(context.startupFailure).message.contains("'doesNotExist'")
    }
  }

  def 'the conditions of scanned rules apply: #configurations.simpleName with #properties'() {
    expect:
    runner.withUserConfiguration(configurations as Class[]).withPropertyValues(properties as String[]).run { context ->
      assert context.getBeanNamesForType(BusinessRule) as Set == expected*.name as Set
    }

    where:
    configurations                   | properties || expected
    [ConditionalPackage]             | [] || []
    [ConditionalPackage]             | ['fixture.rule.enabled=true'] || [OnProperty]
    [ConditionalPackage, Application] | [] || [OnBean, NameUnique, NameShort, NameCapitalized]
  }

  def 'a rule outside the application packages joins as a bean'() {
    when:
    runner.withUserConfiguration(Application, OtherRuleAsBean).run { context ->
      context.getBean(Muster).chain(Object, OtherRule).check('anything')
    }

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.code() == ['other.rule']
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
      assert getMostSpecificCause(context.startupFailure).message ==
      "the rule bean '$name' has the scope 'prototype'; Muster creates each rule once and keeps it, so a rule bean must be a singleton"
    }

    where:
    configuration || name
    PrototypePackage || PrototypeRule.name
    PrototypeBean || 'otherRule'
  }

  def 'a factory bean that makes a new rule each time is rejected'() {
    expect:
    runner.withUserConfiguration(FactoryOfNewRules).run { context ->
      assert getMostSpecificCause(context.startupFailure).message ==
      "the rule bean 'otherRule' is made anew each time by a factory bean; Muster creates each rule once and keeps it, so a rule bean must be a singleton"
    }
  }

  def 'a rule bean whose type has no @Rule is rejected: #configuration.simpleName'() {
    // The bean 'vague' is created before Muster, so its class is known by then; the outcome must
    // not depend on that.
    expect:
    runner.withUserConfiguration(configuration).run { context ->
      assert getMostSpecificCause(context.startupFailure).message == message
    }

    where:
    configuration || message
    RuleDeclaredAsInterface || "the rule bean 'vague' is declared as me.yakar.muster.rule.BusinessRule, which has no @Rule; declare the rule's own class as the type of its @Bean method"
    UnannotatedRule || "the rule bean 'unannotated' is a me.yakar.muster.spring.fixture.app.Unannotated, which has no @Rule; give the rule an id with @Rule"
  }

  def 'two rule beans of one class stop the application'() {
    expect:
    runner.withUserConfiguration(SameRuleTwice).run { context ->
      assert getMostSpecificCause(context.startupFailure).message ==
      "the rule beans 'first' and 'second' are both a me.yakar.muster.spring.fixture.other.OtherRule; Muster creates each rule once, so define it once"
    }
  }

  def 'a rule bean behind a proxy based on interfaces stops the application with a hint'() {
    expect:
    runner.withUserConfiguration(RuleBehindInterfaceProxy).run { context ->
      assert getMostSpecificCause(context.startupFailure).message ==
      "the rule bean 'otherRule' is a proxy based on interfaces, which is no me.yakar.muster.spring.fixture.other.OtherRule; let Spring proxy classes, as spring.aop.proxy-target-class=true does"
    }
  }

  def 'a Muster of the application replaces the one built here'() {
    when: 'the scanned rule is not registered with it'
    runner.withUserConfiguration(Application, OwnMuster).run { context ->
      assert context.getBeansOfType(Muster).keySet() == ['muster'] as Set
      context.getBean(Muster).chain(Object, OtherRule)
      context.getBean(Muster).chain(Vehicle, NameUnique)
    }

    then:
    thrown(IllegalArgumentException)
  }
}
