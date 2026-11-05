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

import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.spring.MusterAutoConfiguration
import me.yakar.muster.spring.fixture.conditional.OnProperty
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.core.test.tools.CompileWithForkedClassLoader

/**
 * The rule classes of an application processed ahead of time and started from the generated code
 * are those found when it was processed. A JUnit Jupiter test, since only its extension loads the
 * generated code next to the package-private classes it calls.
 */
@CompileWithForkedClassLoader
class RuleScannerAheadOfTimeTest {

  @Configuration(proxyBeanMethods = false)
  @ImportAutoConfiguration(MusterAutoConfiguration)
  @AutoConfigurationPackage(basePackageClasses = OnProperty)
  static class Application {}

  @Test
  void 'a rule found when the application is processed is registered once, by the generated code'() {
    startedAheadOfTime(Application, ['fixture.rule.enabled': 'true']) { context ->
      assert context.getBeanNamesForType(BusinessRule) as List == [OnProperty.name]
      // A scanned definition carries the annotations of its class; a generated one does not.
      assert !(context.beanFactory.getBeanDefinition(OnProperty.name) instanceof AnnotatedBeanDefinition)
    }
  }

  @Test
  void 'a rule that a condition left out when the application was processed is not registered at start'() {
    // Set only when the application starts, after the processing.
    startedAheadOfTime(Application, [:], ['fixture.rule.enabled': 'true']) { context ->
      assert context.getBeanNamesForType(BusinessRule).length == 0
    }
  }
}
