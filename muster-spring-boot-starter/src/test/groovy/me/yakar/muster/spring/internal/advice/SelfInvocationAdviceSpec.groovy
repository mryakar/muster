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

import java.util.logging.Level
import me.yakar.muster.spring.LogCapture
import me.yakar.muster.spring.MusterAutoConfiguration
import me.yakar.muster.spring.fixture.selfinvocation.Barn
import me.yakar.muster.spring.fixture.selfinvocation.BigShed
import me.yakar.muster.spring.fixture.selfinvocation.Careful
import me.yakar.muster.spring.fixture.selfinvocation.Garage
import me.yakar.muster.spring.fixture.selfinvocation.OpenLot
import me.yakar.muster.spring.fixture.selfinvocation.WineCellar
import me.yakar.muster.spring.fixture.selfinvocation.Stacks
import me.yakar.muster.spring.fixture.selfinvocation.Vehicle
import me.yakar.muster.spring.fixture.selfinvocation.VehicleDepot
import org.springframework.beans.factory.BeanCreationException
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import spock.lang.Specification

/** A call of an advised method on {@code this} passes no proxy, so the starter finds it at startup. */
class SelfInvocationAdviceSpec extends Specification {

  static final String FOUND = 'Muster found calls of advised methods through this, which pass no proxy and check no rules: '
  static final String REMEDY = '. Call the method through its bean: inject the bean itself, with @Lazy or an ObjectProvider, or move the method to another bean; or set muster.self-invocation=warn to log such calls instead'
  static final List<String> GARAGE = [
    'Garage.register(String) calls Garage.save(Vehicle) through this',
    'Garage.registerEither(String, boolean) calls Garage.save(Vehicle) through this',
    'Garage.registerExplicitly(String) calls Garage.save(Vehicle) through this',
    'Garage.saver() calls Garage.save(Vehicle) through this',
    'a lambda in Garage.registerLater(String) calls Garage.save(Vehicle) through this',
    'a lambda in Garage.registerNested(String) calls Garage.save(Vehicle) through this'
  ]

  @Configuration(proxyBeanMethods = false)
  @AutoConfigurationPackage(basePackageClasses = Vehicle)
  static class Application {}

  @Configuration(proxyBeanMethods = false)
  static class LazyBeans {
    @Bean
    static LazyInitializationBeanFactoryPostProcessor lazyInitialization() {
      new LazyInitializationBeanFactoryPostProcessor()
    }
  }

  def runner = new ApplicationContextRunner()
  .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))
  .withUserConfiguration(Application)

  def 'the application does not start while a method calls an advised method on this, and the error names every call'() {
    expect:
    runner.withBean(Garage).run { context ->
      assert getMostSpecificCause(context.startupFailure).message == FOUND + GARAGE.join('; ') + REMEDY
    }
  }

  def 'the calls of #bean.simpleName are found through its superclasses and interfaces, but not the calls of the method itself with its own input'() {
    expect:
    runner.withBean(bean).run { context ->
      assert getMostSpecificCause(context.startupFailure).message == FOUND + calls.join('; ') + REMEDY
    }

    where:
    bean || calls
    Barn || [
      'Registering.registerFromTop(String) calls Barn.save(Vehicle) through this',
      'Saving.registerDefault(String) calls Barn.save(Vehicle) through this'
    ]
    VehicleDepot || ['Depot.registerFromBase(String) calls VehicleDepot.save(Vehicle) through this']
    BigShed || [
      'BigShed.saveTrimmed(Vehicle) calls BigShed.saveTrimmed(Vehicle) through this',
      'BigShed.saveTwin(Vehicle) calls BigShed.save(Vehicle) through this'
    ]
    Stacks || [
      'Stacks.arithmetic(long, double, float, int, short, byte, char)',
      'Stacks.arrays(Object)',
      'Stacks.assignment()',
      'Stacks.compound(int)',
      'Stacks.discarding()',
      'Stacks.finallySaving(String)',
      'Stacks.guarded(Vehicle)',
      'Stacks.looping(int)',
      'Stacks.switching(int, String)',
      'Stacks.wideCompound(int)',
      'Stacks.wideField()'
    ].collect {
      "$it calls Stacks.save(Vehicle) through this"
    }
  }

  def 'the calls of every advised class are named at once'() {
    expect:
    runner.withBean(Barn).withBean(VehicleDepot).run { context ->
      assert getMostSpecificCause(context.startupFailure).message == FOUND +
      'Depot.registerFromBase(String) calls VehicleDepot.save(Vehicle) through this; Registering.registerFromTop(String) calls Barn.save(Vehicle) through this; Saving.registerDefault(String) calls Barn.save(Vehicle) through this' + REMEDY
    }
  }

  def 'with muster.self-invocation=warn, the application starts and each call is logged'() {
    given:
    def logs = new LogCapture()

    expect:
    runner.withPropertyValues('muster.self-invocation=warn').withBean(Garage).run { context ->
      def garage = context.getBean(Garage)
      garage.register('Boreas')
      assert garage.saved()*.name() == ['Boreas']
      assert warnings(logs) == GARAGE.collect {
        "$it, which passes no proxy and checks no rules; call the method through its bean".toString()
      }
    }

    cleanup:
    logs.close()
  }

  def 'with muster.self-invocation=off, nothing is scanned'() {
    given:
    def logs = new LogCapture()

    expect:
    runner.withPropertyValues('muster.self-invocation=off').withBean(Garage).run { context ->
      context.getBean(Garage).register('Boreas')
      assert warnings(logs) == []
    }

    cleanup:
    logs.close()
  }

  def 'a call through the proxy, on another instance or from a static method is no self-invocation'() {
    expect:
    runner.withBean(Careful).run { context ->
      def careful = context.getBean(Careful)
      careful.register('Boreas')
      assert careful.saved()*.name() == ['Boreas']
    }
  }

  def 'a bean created after the application started is refused when it is created, every time'() {
    expect:
    runner.withUserConfiguration(LazyBeans).withBean(Garage).run { context ->
      assert context.startupFailure == null
      2.times {
        try {
          context.getBean(Garage)
          assert false
        } catch (BeanCreationException e) {
          assert getMostSpecificCause(e).message == FOUND + GARAGE.join('; ') + REMEDY
        }
      }
    }
  }

  def 'a call through the proxy of a base, a private or a package-private method of it is no self-invocation: #bean.simpleName'() {
    expect:
    runner.withBean(bean).run { context ->
      assert context.startupFailure == null
    }

    where:
    bean << [WineCellar, OpenLot]
  }

  def 'a value that is no mode stops the application'() {
    expect:
    runner.withPropertyValues('muster.self-invocation=maybe').withBean(Careful).run { context ->
      assert context.startupFailure != null
      assert getMostSpecificCause(context.startupFailure).message.contains('maybe')
    }
  }

  /** The warnings about calls through this; Muster also warns that no rule binds to a vehicle. */
  private static List<String> warnings(LogCapture logs) {
    logs.records.findAll {
      it.level == Level.WARNING && it.message.contains('through this')
    }*.message
  }
}
