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
package me.yakar.muster.spring.internal

import me.yakar.muster.BusinessRuleViolationException
import me.yakar.muster.Muster
import me.yakar.muster.spring.MusterAutoConfiguration
import me.yakar.muster.spring.MusterAutoConfigurationSpec
import me.yakar.muster.spring.fixture.app.NameUnique
import me.yakar.muster.spring.fixture.app.Names
import me.yakar.muster.spring.fixture.app.Vehicle
import me.yakar.muster.spring.fixture.other.InvalidId
import me.yakar.muster.spring.fixture.other.WithHyphen
import me.yakar.muster.spring.fixture.other.WithoutHyphen
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource
import spock.lang.Specification

class SettingsSpec extends Specification {

  /** Taken, too long and not capitalized: it violates all three rules of the application. */
  static final Vehicle BAD = new Vehicle('a-name-that-is-taken-and-long')

  @Configuration(proxyBeanMethods = false)
  @AutoConfigurationPackage(basePackageClasses = NameUnique)
  static class Application {
    @Bean
    Names names() {
      new Names([BAD.name()] as Set)
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class InvalidIdRule {
    @Bean
    InvalidId invalidId() {
      new InvalidId()
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class IdsOneHyphenApart {
    @Bean
    WithHyphen withHyphen() {
      new WithHyphen()
    }

    @Bean
    WithoutHyphen withoutHyphen() {
      new WithoutHyphen()
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class OwnMuster {
    @Bean
    Muster muster() {
      Muster.builder().build()
    }
  }

  def runner = new ApplicationContextRunner()
  .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))
  .withUserConfiguration(Application)

  /** Checks with a Muster or a chain. */
  static List<String> violated(checker, Vehicle vehicle = BAD) {
    try {
      checker.check(vehicle)
      []
    } catch (BusinessRuleViolationException e) {
      e.violations()*.code()
    }
  }

  static ApplicationContextRunner withEnvironmentVariables(ApplicationContextRunner runner, Map<String, Object> variables) {
    runner.withInitializer {
      it.environment.propertySources.replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
          new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables))
    }
  }

  def 'all rules are on and the mode is fail-fast unless configured'() {
    expect:
    runner.run {
      assert violated(it.getBean(Muster)) == ['vehicle.name-capitalized']
    }
  }

  def 'a property switches a rule off: #property'() {
    expect:
    runner.withPropertyValues(property).run { context ->
      assert violated(context.getBean(Muster).chain(Vehicle, NameUnique)) == expected
    }

    where:
    property || expected
    'muster.rules.vehicle.name-unique.enabled=false' || []
    'muster.rules.vehicle.name-unique.enabled=true' || ['vehicle.name-unique']
    'muster.rules.vehicle.name-unique.enabled=FALSE' || []
  }

  def 'an environment variable switches a rule off: #variable'() {
    expect:
    withEnvironmentVariables(runner, [(variable): 'false']).run { context ->
      assert violated(context.getBean(Muster).chain(Vehicle, NameUnique)) == []
    }

    where:
    variable << [
      'MUSTER_RULES_VEHICLE_NAMEUNIQUE_ENABLED',
      'muster_rules_vehicle_nameunique_enabled'
    ]
  }

  def 'switching rules off leaves the others on'() {
    expect:
    runner.withPropertyValues(
        'muster.rules.vehicle.name-capitalized.enabled=false',
        'muster.rules.vehicle.name-short.enabled=false').run { context ->
          assert violated(context.getBean(Muster)) == ['vehicle.name-unique']
        }
  }

  def 'a key that switches no rule stops the application: #keys'() {
    expect:
    runner.withPropertyValues(keys.collect {
      "$it=false".toString()
    } as String[]).run { context ->
      assert MusterAutoConfigurationSpec.rootCause(context.startupFailure).message ==
      "no rule has the key $listed; a rule is switched with muster.rules.<rule id>.enabled, and the rule ids are vehicle.name-capitalized, vehicle.name-short, vehicle.name-unique"
    }

    where:
    keys || listed
    [
      'muster.rules.vehicle.name-uniqe.enabled'
    ] || 'muster.rules.vehicle.name-uniqe.enabled'
    [
      'muster.rules.vehicle.name-unique.enable'
    ] || 'muster.rules.vehicle.name-unique.enable'
    [
      'muster.rules.vehicle.enabled',
      'muster.rules.vehicle.name-unique.enabled'
    ] || 'muster.rules.vehicle.enabled'
    [
      'muster.rules.b.enabled',
      'muster.rules.a.enabled'
    ] || 'muster.rules.a.enabled, muster.rules.b.enabled'
  }

  def 'a key set to nothing stops the application: #property'() {
    expect:
    runner.withPropertyValues(property).run { context ->
      assert MusterAutoConfigurationSpec.rootCause(context.startupFailure).message == message
    }

    where:
    property || message
    'muster.rules.vehicle.name-unique.enabled=' || 'muster.rules.vehicle.name-unique.enabled is set to nothing; set it to true or false'
    'muster.rules.vehicle.name-unique.enabled= ' || 'muster.rules.vehicle.name-unique.enabled is set to nothing; set it to true or false'
    'muster.mode=' || 'muster.mode is set to nothing; set it to fail-fast or collect-all'
    'muster.transaction.reorder-advisor=' || 'muster.transaction.reorder-advisor is set to nothing; set it to true or false'
  }

  def 'muster.rules itself switches no rule'() {
    expect:
    runner.withPropertyValues('muster.rules=false').run { context ->
      assert MusterAutoConfigurationSpec.rootCause(context.startupFailure).message.startsWith('no rule has the key muster.rules;')
    }
  }

  def 'a key set to nothing is fine where a source that comes first sets it'() {
    expect: 'test properties come before environment variables'
    withEnvironmentVariables(runner, [MUSTER_RULES_VEHICLE_NAMEUNIQUE_ENABLED: ''])
    .withPropertyValues('muster.rules.vehicle.name-unique.enabled=false').run { context ->
      assert violated(context.getBean(Muster).chain(Vehicle, NameUnique)) == []
    }
  }

  def 'an environment variable that switches no rule stops the application too'() {
    expect:
    withEnvironmentVariables(runner, [MUSTER_RULES_VEHICLE_NAME_UNIQUE_ENABLED: 'false']).run { context ->
      assert MusterAutoConfigurationSpec.rootCause(context.startupFailure).message.startsWith(
      'no rule has the key muster.rules.vehicle.name.unique.enabled;')
    }
  }

  def 'muster.mode sets the mode: #value'() {
    expect:
    runner.withPropertyValues("muster.mode=$value").run { context ->
      assert violated(context.getBean(Muster)) == expected
    }

    where:
    value || expected
    'fail-fast' || ['vehicle.name-capitalized']
    'collect-all' || [
      'vehicle.name-capitalized',
      'vehicle.name-short',
      'vehicle.name-unique'
    ]
    'COLLECT_ALL' || [
      'vehicle.name-capitalized',
      'vehicle.name-short',
      'vehicle.name-unique'
    ]
  }

  def 'a mode that does not exist stops the application'() {
    expect:
    runner.withPropertyValues('muster.mode=lenient').run { context ->
      assert context.startupFailure != null
    }
  }

  def 'two rule ids that configuration cannot tell apart stop the application'() {
    expect:
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))
        .withUserConfiguration(IdsOneHyphenApart).run { context ->
          assert MusterAutoConfigurationSpec.rootCause(context.startupFailure).message ==
          "the rules 'vehicle.name-unique' and 'vehicle.nameunique' share the key muster.rules.vehicle.nameunique.enabled, since configuration ignores hyphens; give one of them another id"
        }
  }

  def 'a rule with an invalid id is rejected by Muster, not by the configuration'() {
    expect:
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))
        .withUserConfiguration(InvalidIdRule).run { context ->
          def cause = MusterAutoConfigurationSpec.rootCause(context.startupFailure)
          assert cause instanceof IllegalArgumentException
          assert cause.message.contains("'Not Valid'")
        }
  }

  def 'with a Muster of its own, the application does not start if it configures the one built here: #properties'() {
    expect:
    runner.withUserConfiguration(OwnMuster).withPropertyValues(properties as String[]).run { context ->
      assert MusterAutoConfigurationSpec.rootCause(context.startupFailure).message ==
      "$keys configure only the Muster that the starter builds, but the application defines a Muster bean of its own; remove these properties, or that bean"
    }

    where:
    properties || keys
    ['muster.mode=collect-all'] || 'muster.mode'
    [
      'muster.rules.vehicle.name-unique.enabled=false',
      'muster.mode=fail-fast'
    ] || 'muster.mode, muster.rules.vehicle.name-unique.enabled'
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
}
