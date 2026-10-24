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

import org.aopalliance.intercept.MethodInvocation
import org.springframework.beans.factory.ObjectProvider
import spock.lang.Specification

class RulesInterceptorSpec extends Specification {

  def interceptor = new RulesInterceptor({
    -> null
  } as ObjectProvider, {
    -> null
  } as ObjectProvider)

  def 'a call to a method Muster has not prepared fails rather than skip its rules: target #target'() {
    given:
    def method = Object.getMethod('toString')
    def invocation = [getThis: {
        -> target
      }, getMethod: {
        -> method
      }] as MethodInvocation

    when:
    interceptor.invoke(invocation)

    then:
    def e = thrown(IllegalStateException)
    e.message == 'Muster has not prepared the rules of public java.lang.String java.lang.Object.toString()'

    where:
    target << [new Object(), null]
  }
}
