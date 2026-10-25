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
import me.yakar.muster.spring.fixture.transaction.FakeTransactionManager
import org.springframework.beans.factory.ObjectProvider
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor
import org.springframework.transaction.interceptor.DefaultTransactionAttribute
import org.springframework.transaction.interceptor.TransactionAttributeSource
import org.springframework.transaction.interceptor.TransactionInterceptor
import spock.lang.Specification

class TransactionGuardSpec extends Specification {

  static Method method = Object.getMethod('toString')

  static TransactionGuard guard(Integer propagation) {
    def source = { Method m, Class<?> c ->
      propagation == null ? null : new DefaultTransactionAttribute(propagation)
    } as TransactionAttributeSource
    def advisor = new BeanFactoryTransactionAttributeSourceAdvisor()
    advisor.advice = new TransactionInterceptor(new FakeTransactionManager(), source)
    new TransactionGuard([getIfUnique: { Object[] args ->
        advisor
      }] as ObjectProvider)
  }

  def 'a method requires a transaction only for propagations that start one: #name'() {
    given:
    def guard = guard(propagation)

    expect:
    guard.resolve().forMethod(method, Object).is(requires ? guard : null)

    where:
    name            | propagation || requires
    'REQUIRED'      | TransactionDefinition.PROPAGATION_REQUIRED || true
    'REQUIRES_NEW'  | TransactionDefinition.PROPAGATION_REQUIRES_NEW || true
    'MANDATORY'     | TransactionDefinition.PROPAGATION_MANDATORY || true
    'NESTED'        | TransactionDefinition.PROPAGATION_NESTED || true
    'SUPPORTS'      | TransactionDefinition.PROPAGATION_SUPPORTS || false
    'NOT_SUPPORTED' | TransactionDefinition.PROPAGATION_NOT_SUPPORTED || false
    'NEVER'         | TransactionDefinition.PROPAGATION_NEVER || false
    'none'          | null || false
  }

  def 'without transaction advice, there is nothing to resolve'() {
    expect:
    new TransactionGuard([getIfUnique: { Object[] args ->
        null
      }] as ObjectProvider).resolve() == null
  }
}
