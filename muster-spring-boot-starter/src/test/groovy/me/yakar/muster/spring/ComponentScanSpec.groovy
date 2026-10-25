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

import me.yakar.muster.Muster
import me.yakar.muster.spring.internal.advice.AdviceConfiguration
import me.yakar.muster.spring.internal.engine.EngineConfiguration
import me.yakar.muster.spring.internal.problem.ProblemDetailsConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.context.TypeExcludeFilter
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import spock.lang.Specification

/** An application that scans the packages of Muster for components gets the starter only once. */
class ComponentScanSpec extends Specification {

  /** Scans as {@code @SpringBootApplication} does, leaving out the classes of these tests. */
  @Configuration(proxyBeanMethods = false)
  @ComponentScan(basePackages = 'me.yakar', excludeFilters = [
    @ComponentScan.Filter(type = FilterType.CUSTOM, classes = [TypeExcludeFilter, AutoConfigurationExcludeFilter]),
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = 'me\\.yakar\\.muster\\.spring\\.(fixture\\..*|.*Spec(\\$.*)?)')
  ])
  static class ScansMuster {}

  def 'the configuration classes of the starter are imported, not scanned'() {
    expect:
    new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))
        .withUserConfiguration(ScansMuster)
        .run { context ->
          assert context.startupFailure == null
          def beanFactory = context.beanFactory
          def starter = beanFactory.beanDefinitionNames.findAll {
            def definition = beanFactory.getBeanDefinition(it)
            definition.factoryMethodName == null && definition.beanClassName ==~ /me\.yakar\.muster\.spring\..*Configuration(\$\w+)?/
          }
          // A scanned class would be named after its simple name.
          assert starter as Set == [
            MusterAutoConfiguration.name,
            EngineConfiguration.name,
            EngineConfiguration.name + '$SecurityConfiguration',
            AdviceConfiguration.name,
            AdviceConfiguration.name + '$TransactionConfiguration',
            ProblemDetailsConfiguration.name
          ] as Set
          assert context.getBeanNamesForType(Muster) as List == ['muster']
        }
  }
}
