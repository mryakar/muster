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

import static me.yakar.muster.spring.AheadOfTime.startedAheadOfTime
import static org.junit.jupiter.api.Assertions.assertThrows

import me.yakar.muster.CheckMode
import me.yakar.muster.Muster
import me.yakar.muster.spring.MusterAutoConfiguration
import me.yakar.muster.spring.fixture.validation.ValidationProbe
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.test.tools.CompileWithForkedClassLoader

/**
 * The properties that configure the starter's {@code Muster}, in an application processed ahead of
 * time and started from the generated code. A JUnit Jupiter test, since only its extension loads the
 * generated code next to the package-private classes it calls.
 */
@CompileWithForkedClassLoader
class OwnMusterCheckAheadOfTimeTest {

  static final Map<String, Object> PROPERTIES = [
    'muster.mode': 'collect-all',
    'muster.rules.validation.probe.enabled': 'false'
  ]

  @Configuration(proxyBeanMethods = false)
  @ImportAutoConfiguration(MusterAutoConfiguration)
  @AutoConfigurationPackage(basePackageClasses = ValidationProbe)
  static class Application {}

  @Configuration(proxyBeanMethods = false)
  @ImportAutoConfiguration(MusterAutoConfiguration)
  static class ApplicationWithOwnMuster {
    @Bean
    Muster muster() {
      Muster.builder().build()
    }
  }

  @Test
  void 'the Muster built here takes the properties'() {
    startedAheadOfTime(Application, PROPERTIES) { context ->
      def muster = context.getBean(Muster)
      assert muster.mode() == CheckMode.COLLECT_ALL
      assert muster.disabledRuleIds() == ['validation.probe'] as Set
    }
  }

  @Test
  void 'with a Muster of its own, the application does not start if it configures the one built here'() {
    // Set only when the application starts, after the processing.
    def failure = assertThrows(IllegalStateException) {
      startedAheadOfTime(ApplicationWithOwnMuster, [:], ['muster.mode': 'collect-all']) {}
    }
    assert failure.message.contains('the application defines the Muster bean \'muster\' of its own')
  }
}
