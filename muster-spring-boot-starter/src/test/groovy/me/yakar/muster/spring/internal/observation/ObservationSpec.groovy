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
package me.yakar.muster.spring.internal.observation

import static me.yakar.muster.spring.Concurrency.concurrently

import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.tck.TestObservationRegistry
import java.util.concurrent.CopyOnWriteArrayList
import me.yakar.muster.BusinessRuleViolationException
import me.yakar.muster.Muster
import me.yakar.muster.spi.CheckListener
import me.yakar.muster.spring.MusterAutoConfiguration
import me.yakar.muster.spring.fixture.call.ReportsCall
import me.yakar.muster.spring.fixture.observation.CodeFree
import me.yakar.muster.spring.fixture.observation.Shipment
import me.yakar.muster.spring.fixture.observation.ShipmentService
import me.yakar.muster.spring.fixture.observation.Traced
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import spock.lang.Specification

/** Checks and rules observed with Micrometer Observation, when the application has a registry. */
class ObservationSpec extends Specification {

  @Configuration(proxyBeanMethods = false)
  @AutoConfigurationPackage(basePackageClasses = Shipment)
  static class Application {
    @Bean
    ShipmentService shipmentService() {
      new ShipmentService()
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class Registry {
    @Bean
    TestObservationRegistry observationRegistry() {
      TestObservationRegistry.create()
    }
  }

  /** The application without the rule that reads the registry, for a class path without Micrometer. */
  @Configuration(proxyBeanMethods = false)
  @AutoConfigurationPackage(basePackageClasses = ReportsCall)
  static class WithoutMicrometer {}

  /** Notes, for each check, its name and which observation was in scope when it started, in one list for all. */
  static class Noting implements CheckListener {
    static final List<List> STARTED = new CopyOnWriteArrayList<>()

    final String name
    final ObservationRegistry registry

    Noting(String name, ObservationRegistry registry) {
      this.name = name
      this.registry = registry
    }

    CheckListener.Observer checkStarted(CheckListener.Check check) {
      STARTED << [name, registry.currentObservation?.context?.name]
      null
    }
  }

  def setup() {
    Noting.STARTED.clear()
  }

  @Configuration(proxyBeanMethods = false)
  static class OwnListeners {
    @Bean
    @Order(2)
    Noting second(ObservationRegistry registry) {
      new Noting('second', registry)
    }

    @Bean
    @Order(1)
    Noting first(ObservationRegistry registry) {
      new Noting('first', registry)
    }

    @Bean
    Noting unordered(ObservationRegistry registry) {
      new Noting('unordered', registry)
    }
  }

  def runner = new ApplicationContextRunner()
  .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))
  .withUserConfiguration(Application, Registry)

  def 'a check opens an observation, and each of its rules a child, with the outcome of each'() {
    expect:
    runner.run { context ->
      def registry = context.getBean(TestObservationRegistry)
      context.getBean(ShipmentService).create(new Shipment('OK'))
      def observed = observed(registry)
      assert observed*.name == ['muster.check', 'muster.rule', 'muster.rule', 'muster.rule', 'muster.rule']
      assert observed*.contextualName == [
        'ShipmentService.create(Shipment)',
        'shipment.code-format',
        'shipment.code-free',
        'shipment.imported',
        'shipment.traced'
      ]
      assert observed*.tags == [
        [name: 'ShipmentService.create(Shipment)', outcome: 'passed'],
        [rule: 'shipment.code-format', outcome: 'passed'],
        [rule: 'shipment.code-free', outcome: 'passed'],
        [rule: 'shipment.imported', outcome: 'skipped'],
        [rule: 'shipment.traced', outcome: 'passed'],
      ]
      assert observed*.parent == [null] + ['ShipmentService.create(Shipment)'] * 4
      assert observed*.error == [null] * 5
      assert observed*.stopped == [true] * 5
      assert registry.currentObservation == null
    }
  }

  def 'a rule runs in the scope of its observation, under the check'() {
    expect:
    runner.run { context ->
      context.getBean(ShipmentService).create(new Shipment('OK'))
      assert context.getBean(Traced).seen == [['muster.rule', 'muster.check']]
    }
  }

  def 'a violated rule and its check end failed, without an error'() {
    expect:
    runner.run { context ->
      def registry = context.getBean(TestObservationRegistry)
      try {
        context.getBean(ShipmentService).create(new Shipment('bad code'))
        assert false
      } catch (BusinessRuleViolationException expected) {
        assert expected.violations()*.ruleId() == ['shipment.code-format']
      }
      def observed = observed(registry)
      assert observed*.contextualName == ['ShipmentService.create(Shipment)', 'shipment.code-format']
      assert observed*.tags.outcome == ['failed', 'failed']
      assert observed*.error == [null, null]
      assert registry.currentObservation == null
    }
  }

  def 'in collect-all mode a skipped rule is observed as skipped'() {
    expect:
    runner.withPropertyValues('muster.mode=collect-all').run { context ->
      def registry = context.getBean(TestObservationRegistry)
      try {
        context.getBean(ShipmentService).create(new Shipment('bad code'))
        assert false
      } catch (BusinessRuleViolationException expected) {
        assert expected.violations()*.ruleId() == ['shipment.code-format']
      }
      assert observed(registry)*.tags.outcome == ['failed', 'failed', 'skipped', 'skipped', 'passed']
    }
  }

  def 'a rule that throws ends its observation and the check\'s with the error'() {
    expect:
    runner.run { context ->
      def registry = context.getBean(TestObservationRegistry)
      try {
        context.getBean(ShipmentService).create(new Shipment('BOOM'))
        assert false
      } catch (IllegalStateException expected) {
        assert expected.is(CodeFree.FAILURE)
      }
      def observed = observed(registry)
      assert observed*.contextualName == ['ShipmentService.create(Shipment)', 'shipment.code-format', 'shipment.code-free']
      assert observed*.tags.outcome == ['error', 'passed', 'error']
      assert observed*.error == [CodeFree.FAILURE, null, CodeFree.FAILURE]
      assert observed*.stopped == [true] * 3
      assert registry.currentObservation == null
    }
  }

  def 'a registry whose predicate leaves muster.check out observes nothing of a check'() {
    expect:
    runner.run { context ->
      def registry = context.getBean(TestObservationRegistry)
      registry.observationConfig().observationPredicate { name, observationContext ->
        name != 'muster.check'
      }
      context.getBean(ShipmentService).create(new Shipment('OK'))
      assert registry.contexts.empty
      assert context.getBean(Traced).seen == [[null, null]]
    }
  }

  def 'the listeners of the application are told in their order, before the starter\'s'() {
    expect:
    runner.withUserConfiguration(OwnListeners).run { context ->
      context.getBean(ShipmentService).create(new Shipment('OK'))
      assert Noting.STARTED == [['first', null], ['second', null], ['unordered', null]]
      assert context.getBean(TestObservationRegistry).contexts.size() == 5
    }
  }

  def 'without an ObservationRegistry nothing is observed, and the listeners of the application are still told'() {
    expect:
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))
        .withUserConfiguration(Application)
        .withBean('own', Noting, { new Noting('own', ObservationRegistry.NOOP) })
        .run { context ->
          assert context.getBeansOfType(CheckListener).keySet() == ['own'] as Set
          context.getBean(ShipmentService).create(new Shipment('OK'))
          assert Noting.STARTED == [['own', null]]
        }
  }

  def 'without Micrometer on the class path the application starts, and checks'() {
    expect:
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))
        .withUserConfiguration(WithoutMicrometer)
        .withClassLoader(new FilteredClassLoader(ObservationRegistry))
        .run { context ->
          assert context.getBeansOfType(CheckListener).isEmpty()
          assert context.getBean(Muster) != null
        }
  }

  def 'checks on many threads at once are observed apart'() {
    expect:
    runner.run { context ->
      def registry = context.getBean(TestObservationRegistry)
      def service = context.getBean(ShipmentService)
      def seen = concurrently { n ->
        service.create(new Shipment('OK'))
        registry.currentObservation
      }
      assert seen == [null] * 16
      def checks = registry.contexts.findAll { it.context.name == 'muster.check' }*.context
      def rules = registry.contexts.findAll { it.context.name == 'muster.rule' }*.context
      assert checks.size() == 16
      // The four rules of each check hang on that check, not on another thread's.
      def byParent = rules.groupBy { it.parentObservation.context }
      assert byParent.keySet() == checks as Set
      assert byParent.values()*.size() == [4] * 16
      assert registry.contexts.every { it.observationStopped }
    }
  }

  /** The observations of a registry, in the order they started: name, contextual name, tags, parent, error, stopped. */
  private static List<Map> observed(TestObservationRegistry registry) {
    registry.contexts.collect { observed ->
      def c = observed.context
      [
        name          : c.name,
        contextualName: c.contextualName,
        tags          : c.lowCardinalityKeyValues.collectEntries { [(it.key): it.value] },
        parent        : c.parentObservation?.context?.contextualName,
        error         : c.error,
        stopped       : observed.observationStopped,
      ]
    }
  }
}
