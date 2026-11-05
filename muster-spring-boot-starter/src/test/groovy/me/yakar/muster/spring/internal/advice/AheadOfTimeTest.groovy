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

import static me.yakar.muster.spring.AheadOfTime.startedAheadOfTime
import static org.junit.jupiter.api.Assertions.assertThrows

import me.yakar.muster.spring.MusterAutoConfiguration
import me.yakar.muster.spring.fixture.transaction.FakeTransactionManager
import me.yakar.muster.spring.fixture.validation.Steps
import me.yakar.muster.spring.fixture.validation.ValidatedService
import me.yakar.muster.spring.fixture.validation.ValidationProbe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.test.tools.CompileWithForkedClassLoader
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor

/**
 * An application processed ahead of time, as for a native image, started from the code that the
 * processing generates: the constraints are checked before the rules, and the rules run inside the
 * transaction, or the application does not start. A JUnit Jupiter test, since only its extension
 * loads the generated code next to the package-private classes it calls.
 */
@CompileWithForkedClassLoader
class AheadOfTimeTest {

  @Configuration(proxyBeanMethods = false)
  @ImportAutoConfiguration([MusterAutoConfiguration, ValidationAutoConfiguration])
  @AutoConfigurationPackage(basePackageClasses = ValidationProbe)
  @EnableTransactionManagement
  static class Application {
    @Bean
    ValidatedService service() {
      new ValidatedService()
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      new FakeTransactionManager()
    }
  }

  /** The application gives the transaction advice an order of its own, which Muster leaves alone. */
  @Configuration(proxyBeanMethods = false)
  @ImportAutoConfiguration([MusterAutoConfiguration, ValidationAutoConfiguration])
  @AutoConfigurationPackage(basePackageClasses = ValidationProbe)
  @EnableTransactionManagement(order = 0)
  static class ApplicationWithOrder {
    @Bean
    ValidatedService service() {
      new ValidatedService()
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      new FakeTransactionManager()
    }
  }

  @BeforeEach
  void clearSteps() {
    Steps.NOTED.clear()
  }

  @Test
  void 'the constraints are checked before the rules, which run inside the transaction'() {
    startedAheadOfTime(Application) { context ->
      context.getBean(ValidatedService).createInTransaction('Atlas')
      assert Steps.NOTED == ['constraint', 'rule in transaction']
    }
  }

  @Test
  void 'an order the application gives the transaction advice is left alone'() {
    startedAheadOfTime(ApplicationWithOrder) { context ->
      assert context.getBean(BeanFactoryTransactionAttributeSourceAdvisor).order == 0
      context.getBean(ValidatedService).createInTransaction('Atlas')
      assert Steps.NOTED.last() == 'rule in transaction'
    }
  }

  @Test
  void 'the start fails if the transaction advice would not run around Muster\'s'() {
    // Read when the application is processed, which leaves the order the application starts with.
    def failure = assertThrows(IllegalStateException) {
      startedAheadOfTime(Application, ['muster.transaction.reorder-advisor': 'false'], [:]) {}
    }
    assert failure.message.contains('does not come before Muster\'s')
  }
}
