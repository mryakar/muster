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

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause

import me.yakar.muster.BusinessRuleViolationException
import me.yakar.muster.spring.MusterAutoConfiguration
import me.yakar.muster.spring.fixture.collection.AddVehicle
import me.yakar.muster.spring.fixture.collection.FleetComponent
import me.yakar.muster.spring.fixture.collection.FleetService
import me.yakar.muster.spring.fixture.collection.LowerBoundList
import me.yakar.muster.spring.fixture.collection.NameFree
import me.yakar.muster.spring.fixture.collection.OpenList
import me.yakar.muster.spring.fixture.collection.RawList
import me.yakar.muster.spring.fixture.collection.VehicleStore
import me.yakar.muster.spring.fixture.collection.WildcardList
import me.yakar.muster.spring.fixture.collection.WrongRule
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import spock.lang.Specification

/** Methods that take a collection: rules of its elements and of the whole collection. */
class CollectionAdviceSpec extends Specification {

  static final AddVehicle FREE = new AddVehicle('Boreas')
  static final AddVehicle TAKEN = new AddVehicle('Atlas')

  @Configuration(proxyBeanMethods = false)
  @AutoConfigurationPackage(basePackageClasses = NameFree)
  @Import([FleetService, FleetComponent, VehicleStore])
  static class Application {}

  @Configuration(proxyBeanMethods = false)
  @AutoConfigurationPackage(basePackageClasses = NameFree)
  static class RulePackage {}

  def runner = new ApplicationContextRunner()
  .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))
  .withUserConfiguration(Application)

  /** Returns the rule id and target of each violation that the call throws, or none if it passes. */
  static List<String> violated(Closure call) {
    try {
      call()
      []
    } catch (BusinessRuleViolationException e) {
      e.violations().collect { it.ruleId() + ' ' + it.target().orElse('-') }
    }
  }

  def 'a service method that takes a collection of rule targets checks the rules of its elements and of the whole collection: #method'() {
    expect:
    runner.run { context ->
      def service = context.getBean(FleetService)
      assert violated { service."$method"(inputs([FREE, TAKEN])) } == ['fleet.name-free [1].name']
      assert violated {
        service."$method"(inputs([FREE, new AddVehicle('Zeus'), new AddVehicle('Hermes')]))
      } == ['fleet.batch-limit -']
      assert service.calls.empty
      service."$method"(inputs([FREE]))
      assert service.calls == [inputs([FREE])]
    }

    where:
    method        | inputs
    'addAll'      | { List it -> it }
    'addDistinct' | { List it -> new LinkedHashSet(it) }
    'addSome'     | { List it -> it }
  }

  def 'a method checks the rules it lists for a collection, of its elements and of the whole'() {
    expect:
    runner.run { context ->
      def service = context.getBean(FleetService)
      assert violated { service.deleteAll([1L, 404L]) } == ['fleet.vehicle-exists [1]']
      assert violated {
        service.deleteFew([404L, 2L, 3L])
      } == ['fleet.vehicle-exists [0]', 'fleet.ids-limit -']
      service.deleteAll([1L, 2L])
      assert service.calls == [[1L, 2L]]
    }
  }

  def 'a method that excludes a rule of the whole collection does not check it'() {
    expect:
    runner.run { context ->
      def service = context.getBean(FleetService)
      service.addMany([FREE, new AddVehicle('Zeus'), new AddVehicle('Hermes')])
      assert service.calls.size() == 1
    }
  }

  def 'the element type of a list of a type argument comes from the class'() {
    expect:
    runner.run { context ->
      def store = context.getBean(VehicleStore)
      assert violated { store.storeAll([TAKEN]) } == ['fleet.name-free [0].name']
      store.storeAll([FREE])
      assert store.calls == [[FREE]]
    }
  }

  def 'a collection of what is not a rule target, or a class that is not a service, checks nothing without rules'() {
    expect:
    runner.run { context ->
      def service = context.getBean(FleetService)
      service.describeAll(['Atlas'])
      def component = context.getBean(FleetComponent)
      component.addAll([TAKEN])
      assert service.calls == [['Atlas']]
      assert component.calls == [[TAKEN]]
    }
  }

  def 'a method with rules that takes a collection of an unknown element type stops the application: #bean.simpleName'() {
    expect:
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))
        .withUserConfiguration(RulePackage)
        .withBean(bean)
        .run { context ->
          assert getMostSpecificCause(context.startupFailure).message ==
          "${bean.simpleName}.deleteAll(List) takes a collection whose element type is not known, so Muster cannot tell which rules check its elements; name it, as in List<CreateVehicle>"
        }

    where:
    bean << [RawList, WildcardList, LowerBoundList, OpenList]
  }

  def 'a listed rule that checks neither the elements nor the whole collection stops the application'() {
    expect:
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))
        .withUserConfiguration(RulePackage)
        .withBean(WrongRule)
        .run { context ->
          assert getMostSpecificCause(context.startupFailure).message.startsWith("${NameFree.name} checks")
        }
  }
}
