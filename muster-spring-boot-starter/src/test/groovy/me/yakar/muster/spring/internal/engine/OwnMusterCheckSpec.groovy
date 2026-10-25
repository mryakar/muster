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

import static me.yakar.muster.spring.EnvironmentVariables.withEnvironmentVariables
import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause

import me.yakar.muster.Muster
import me.yakar.muster.spring.MusterAutoConfiguration
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.AnnotationBeanNameGenerator
import org.springframework.context.annotation.AnnotationConfigUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import spock.lang.Specification

class OwnMusterCheckSpec extends Specification {

  @Configuration(proxyBeanMethods = false)
  static class OwnMuster {
    @Bean
    Muster muster() {
      Muster.builder().build()
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class OwnMusterOfOtherName {
    @Bean
    Muster ownMuster() {
      Muster.builder().build()
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class Outer {
    @Configuration(proxyBeanMethods = false)
    static class Nested {
      @Bean
      Muster muster() {
        Muster.builder().build()
      }
    }
  }

  static class MusterFactory implements FactoryBean<Muster> {
    Muster getObject() {
      Muster.builder().build()
    }

    Class<?> getObjectType() {
      Muster
    }
  }

  def runner = new ApplicationContextRunner()
  .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))

  static String message(String keys) {
    "$keys configure only the Muster that the starter builds, but the application defines a Muster bean of its own; remove these properties, or that bean"
  }

  def 'with a Muster of its own, the application does not start if it configures the one built here: #properties'() {
    expect:
    runner.withUserConfiguration(own).withPropertyValues(properties as String[]).run { context ->
      assert getMostSpecificCause(context.startupFailure).message == message(keys)
    }

    where:
    own                  | properties || keys
    OwnMuster            | ['muster.mode=collect-all'] || 'muster.mode'
    OwnMuster            | ['muster.rules=false'] || 'muster.rules'
    OwnMusterOfOtherName | ['muster.rules.vehicle.name-unique.enabled=false', 'muster.mode=fail-fast'] || 'muster.mode, muster.rules.vehicle.name-unique.enabled'
    Outer                | ['muster.mode=collect-all'] || 'muster.mode'
    MusterFactory        | ['muster.mode=collect-all'] || 'muster.mode'
  }

  def 'an environment variable configures the Muster built here too'() {
    expect:
    withEnvironmentVariables(runner.withUserConfiguration(OwnMuster), [MUSTER_MODE: 'collect-all']).run { context ->
      assert getMostSpecificCause(context.startupFailure).message == message('muster.mode')
    }
  }

  def 'a Muster that the application registers without a bean method is its own: #how'() {
    expect:
    registered(runner, how).withPropertyValues('muster.mode=collect-all').run { context ->
      assert getMostSpecificCause(context.startupFailure).message == message('muster.mode')
    }

    where:
    how << ['as a singleton', 'with a bean supplier', 'with a plain definition']
  }

  static ApplicationContextRunner registered(ApplicationContextRunner runner, String how) {
    switch (how) {
      case 'as a singleton':
        return runner.withInitializer {
          it.beanFactory.registerSingleton('ownMuster', Muster.builder().build())
        }
      case 'with a bean supplier':
        return runner.withBean(Muster, { Muster.builder().build() })
      default:
        return runner.withInitializer {
          it.registerBeanDefinition('ownMuster', new RootBeanDefinition(Muster, {
            Muster.builder().build()
          }))
        }
    }
  }

  def 'with a Muster of its own, the application starts with the properties that the starter itself reads: #properties'() {
    expect:
    runner.withUserConfiguration(OwnMuster).withPropertyValues(properties as String[]).run { context ->
      assert context.startupFailure == null
      assert context.getBeansOfType(Muster).keySet() == ['muster'] as Set
    }

    where:
    properties << [
      [],
      [
        'muster.transaction.reorder-advisor=true',
        'muster.problem-details.mixed-status=400'
      ]
    ]
  }

  def 'the Muster built here takes the properties'() {
    expect:
    runner.withPropertyValues('muster.mode=collect-all').run { context ->
      assert context.startupFailure == null
    }
  }

  def 'a child context that uses the Muster of its parent takes the properties too'() {
    expect:
    runner.withPropertyValues('muster.mode=collect-all').run { parent ->
      runner.withParent(parent).withPropertyValues('muster.mode=collect-all').run { child ->
        assert child.startupFailure == null
        assert !child.beanFactory.containsBeanDefinition('muster')
        assert child.getBean(Muster).is(parent.getBean(Muster))
      }
    }
  }

  def 'the Muster built here is found whatever the application names its configuration'() {
    expect:
    runner.withInitializer {
      it.beanFactory.registerSingleton(AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR, new AnnotationBeanNameGenerator())
    }.withPropertyValues('muster.mode=collect-all').run { context ->
      assert context.startupFailure == null
      assert context.beanFactory.getBeanDefinition('muster').factoryBeanName == 'engineConfiguration'
    }
  }
}
