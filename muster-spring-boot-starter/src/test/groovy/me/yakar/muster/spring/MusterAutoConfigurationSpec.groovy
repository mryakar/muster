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
import me.yakar.muster.spring.fixture.app.NameUnique
import me.yakar.muster.spring.fixture.app.Names
import me.yakar.muster.spring.fixture.app.Vehicle
import me.yakar.muster.spring.fixture.other.OtherRule
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import spock.lang.Specification

/** The auto-configuration builds a Muster unless the application has its own. */
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
  static class OwnMuster {
    @Bean
    Muster muster() {
      Muster.builder().rules(new OtherRule()).build()
    }
  }

  def runner = new ApplicationContextRunner()
  .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))

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
