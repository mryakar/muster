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
package me.yakar.muster.spring.internal.engine

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause

import me.yakar.muster.BusinessRuleViolationException
import me.yakar.muster.Muster
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.spring.MusterAutoConfiguration
import me.yakar.muster.spring.fixture.app.NameUnique
import me.yakar.muster.spring.fixture.app.Names
import me.yakar.muster.spring.fixture.other.OtherRule
import me.yakar.muster.spring.fixture.prototype.PrototypeRule
import org.springframework.aop.framework.ProxyFactory
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import spock.lang.Specification

/** Every rule bean joins the Muster of the starter, once, as the rule it is. */
class RuleBeansSpec extends Specification {

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
  @AutoConfigurationPackage(basePackageClasses = NameUnique)
  static class Application {
    @Bean
    Names names() {
      new Names(['Atlas'] as Set)
    }
  }

  def runner = new ApplicationContextRunner()
  .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))

  def 'a rule outside the application packages joins as a bean'() {
    when:
    runner.withUserConfiguration(Application, OtherRuleAsBean).run { context ->
      context.getBean(Muster).chain(Object, OtherRule).check('anything')
    }

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.code() == ['other.rule']
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
}
