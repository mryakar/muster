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

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import me.yakar.muster.Muster
import me.yakar.muster.spring.fixture.advice.VehicleComponent
import org.aopalliance.intercept.MethodInvocation
import org.springframework.beans.factory.ObjectProvider
import spock.lang.Specification

class RulesInterceptorSpec extends Specification {

  def muster = Muster.builder().build()
  def interceptor = new RulesInterceptor(
  [getObject: { Object[] args ->
      muster
    }, getIfAvailable: { Object[] args ->
      null
    }] as ObjectProvider,
  [getObject: { Object[] args ->
      null
    }, getIfAvailable: { Object[] args ->
      null
    }] as ObjectProvider)

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
    e.message == 'Muster has not prepared the rules of public java.lang.String java.lang.Object.toString()'
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
}
