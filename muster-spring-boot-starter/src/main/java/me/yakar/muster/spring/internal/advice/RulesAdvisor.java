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
package me.yakar.muster.spring.internal.advice;

import java.io.Serial;
import java.lang.reflect.Method;
import org.aopalliance.aop.Advice;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.core.Ordered;

/**
 * Applies Muster's advice to the methods it advises. It has the lowest precedence, so that the
 * transaction advice, which comes first, runs around it.
 */
final class RulesAdvisor extends AbstractPointcutAdvisor {

  @Serial private static final long serialVersionUID = 1L;

  static final int ORDER = Ordered.LOWEST_PRECEDENCE;

  private final transient RulesInterceptor interceptor;
  private final transient Pointcut pointcut;

  RulesAdvisor(RulesInterceptor interceptor) {
    this.interceptor = interceptor;
    this.pointcut =
        new StaticMethodMatcherPointcut() {
          @Override
          public boolean matches(Method method, Class<?> targetClass) {
            if (AdvisedMethods.advised(method, targetClass)) {
              interceptor.note(targetClass);
              return true;
            }
            return false;
          }
        };
  }

  @Override
  public int getOrder() {
    return ORDER;
  }

  @Override
  public Pointcut getPointcut() {
    return pointcut;
  }

  @Override
  public Advice getAdvice() {
    return interceptor;
  }
}
