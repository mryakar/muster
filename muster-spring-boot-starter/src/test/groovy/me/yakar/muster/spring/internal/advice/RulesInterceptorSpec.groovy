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

import java.lang.reflect.Method
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import me.yakar.muster.Muster
import me.yakar.muster.spring.Rules
import me.yakar.muster.spring.fixture.advice.SaveVehicle
import me.yakar.muster.spring.fixture.advice.UnregisteredRule
import me.yakar.muster.spring.fixture.advice.VehicleComponent
import me.yakar.muster.spring.fixture.transaction.FakeTransactionManager
import org.aopalliance.intercept.MethodInvocation
import org.springframework.beans.factory.ObjectProvider
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor
import org.springframework.transaction.interceptor.TransactionAttributeSource
import org.springframework.transaction.interceptor.TransactionInterceptor
import me.yakar.muster.spring.internal.selfinvocation.SelfInvocationMode
import spock.lang.Specification

class RulesInterceptorSpec extends Specification {

  static class OtherComponent {
    @Rules
    void checked(SaveVehicle vehicle) {}
  }

  static class LaterComponent {
    @Rules
    void checked(SaveVehicle vehicle) {}
  }

  def muster = Muster.builder().build()
  def interceptor = new RulesInterceptor(
  new AdviceTable(),
  [getObject: { Object[] args ->
      muster
    }, getIfAvailable: { Object[] args ->
      null
    }] as ObjectProvider,
  [getObject: { Object[] args ->
      null
    }, getIfAvailable: { Object[] args ->
      null
    }] as ObjectProvider,
  SelfInvocationMode.FAIL)

  def method = Object.getMethod('toString')

  def 'a call without a target fails rather than skip its rules'() {
    when:
    interceptor.invoke([getThis: {
        -> null
      }, getMethod: {
        -> method
      }] as MethodInvocation)

    then:
    def e = thrown(IllegalStateException)
    e.message == 'Muster cannot check the rules of public java.lang.String java.lang.Object.toString() without a target'
  }

  def 'a call of a method that Muster does not advise fails rather than skip its rules'() {
    when:
    interceptor.invoke([getThis: {
        -> new Object()
      }, getMethod: {
        -> method
      }] as MethodInvocation)

    then:
    def e = thrown(IllegalStateException)
    e.message == 'Muster has not prepared the rules of Object.toString()'
  }

  def 'noting a class that is prepared takes no lock'() {
    given:
    interceptor.note(VehicleComponent)
    interceptor.afterSingletonsInstantiated()
    def held = new CountDownLatch(1)
    def release = new CountDownLatch(1)
    def holder = Thread.start {
      synchronized (interceptor.@lock) {
        held.countDown()
        release.await()
      }
    }
    assert held.await(1, TimeUnit.MINUTES)

    when:
    def noted = CompletableFuture.runAsync {
      interceptor.note(VehicleComponent)
    }

    then:
    noted.get(10, TimeUnit.SECONDS) == null

    cleanup:
    release.countDown()
    holder.join()
  }

  def 'a class that preparing another notes on the same thread joins the running pass'() {
    given: 'a transaction test that notes another class the first time it runs'
    RulesInterceptor reentered
    def noted = false
    def source = { Method m, Class<?> c ->
      if (!noted) {
        noted = true
        reentered.note(LaterComponent)
      }
      null
    } as TransactionAttributeSource
    def advisor = new BeanFactoryTransactionAttributeSourceAdvisor()
    advisor.advice = new TransactionInterceptor(new FakeTransactionManager(), source)
    advisor.order = RulesAdvisor.ORDER - 1
    def guard = new TransactionGuard([getIfUnique: { Object[] args ->
        advisor
      }] as ObjectProvider)
    def resolved = 0
    def table = new AdviceTable()
    reentered = new RulesInterceptor(
        table,
        [getObject: { Object[] args ->
            resolved++
            muster
          }] as ObjectProvider,
        [getIfAvailable: { Object[] args ->
            guard
          }] as ObjectProvider,
        SelfInvocationMode.FAIL)
    reentered.note(VehicleComponent)
    reentered.note(OtherComponent)

    when:
    reentered.afterSingletonsInstantiated()

    then: 'all are prepared, and Muster is got once, before the lock'
    noted
    [VehicleComponent, OtherComponent, LaterComponent].every { table.contains(it) }
    resolved == 1
  }

  def 'a class that fails while another thread prepares it fails on its own thread too: #first.simpleName noted first'() {
    given: 'Muster of the next pass is got only once released, so that pass waits before the lock'
    def blockNext = false
    def entered = new CountDownLatch(1)
    def release = new CountDownLatch(1)
    def table = new AdviceTable()
    def racing = new RulesInterceptor(
        table,
        [getObject: { Object[] args ->
            if (blockNext) {
              blockNext = false
              entered.countDown()
              assert release.await(1, TimeUnit.MINUTES)
            }
            muster
          }] as ObjectProvider,
        [getIfAvailable: { Object[] args ->
            null
          }] as ObjectProvider,
        SelfInvocationMode.FAIL)
    racing.afterSingletonsInstantiated()
    def expected = attempt {
      interceptor.afterSingletonsInstantiated()
      interceptor.note(UnregisteredRule)
    }.get(1, TimeUnit.MINUTES)
    assert expected instanceof IllegalArgumentException

    when: 'the second class is noted, and its pass runs, while the pass of the first waits'
    blockNext = true
    def waiting = attempt { racing.note(first) }
    assert entered.await(1, TimeUnit.MINUTES)
    def passed = attempt { racing.note(second) }.get(1, TimeUnit.MINUTES)
    release.countDown()
    def released = waiting.get(1, TimeUnit.MINUTES)
    def outcomes = [(first): released, (second): passed]

    then: 'the failing class fails on the thread that noted it, with its own error'
    outcomes[UnregisteredRule].getClass() == expected.getClass()
    outcomes[UnregisteredRule].message == expected.message

    and: 'the thread of the other class fails only if its pass met the failing class'
    outcomes[OtherComponent]?.message == (otherMeetsFailure ? expected.message : null)

    and: 'the other class is prepared, and noting it again passes'
    table.contains(OtherComponent)
    !table.contains(UnregisteredRule)
    attempt { racing.note(OtherComponent) }.get(1, TimeUnit.MINUTES) == null

    where:
    first            | second || otherMeetsFailure
    UnregisteredRule | OtherComponent || true
    OtherComponent   | UnregisteredRule || false
  }

  /** Runs the call on a new thread; the future gets what it threw, or null. */
  private static CompletableFuture<Throwable> attempt(Closure<?> call) {
    def future = new CompletableFuture<Throwable>()
    Thread.start {
      try {
        def result = call()
        future.complete(result instanceof Throwable ? result : null)
      } catch (Throwable e) {
        future.complete(e)
      }
    }
    future
  }

  def 'a class that fails to prepare fails every time it is noted, and the classes noted after it are prepared'() {
    given:
    def table = interceptor.@table
    interceptor.afterSingletonsInstantiated()

    when:
    interceptor.note(UnregisteredRule)

    then:
    def first = thrown(IllegalArgumentException)

    when:
    interceptor.note(OtherComponent)

    then:
    table.contains(OtherComponent)

    when:
    interceptor.note(UnregisteredRule)

    then:
    def again = thrown(IllegalArgumentException)
    again.message == first.message
    !table.contains(UnregisteredRule)
  }
}
