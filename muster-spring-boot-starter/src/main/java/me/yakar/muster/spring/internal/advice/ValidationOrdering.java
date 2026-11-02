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

import java.util.function.Predicate;
import me.yakar.muster.spring.internal.Settings;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * Moves the method validation advice ahead of Muster's on the proxies that Muster advises, so that
 * the constraints of a method are checked before its rules, and before its transaction.
 *
 * <p>Spring Boot adds the validation advice after the advice a proxy already has. This
 * post-processor has no order, so it runs after the validation post-processor; it moves the
 * validation advice to just before Muster's, or before the transaction advice when that directly
 * precedes Muster's. Other proxies and other advice keep Spring's order. {@code
 * muster.validation.reorder-advisor=false} turns it off.
 */
final class ValidationOrdering implements BeanPostProcessor, EnvironmentAware {

  private static final String VALIDATION_INTERCEPTOR =
      "org.springframework.validation.beanvalidation.MethodValidationInterceptor";
  private static final String TRANSACTION_ADVISOR =
      "org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor";

  // Set through Aware rather than injected: resolving a parameter of the bean method by type would
  // create factory beans early to learn what they make. Read by every thread that creates a bean.
  private volatile boolean enabled;

  @Override
  public void setEnvironment(Environment environment) {
    enabled = Settings.reorderValidationAdvisor(environment);
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (enabled && bean instanceof Advised advised) {
      var advisors = advised.getAdvisors();
      int rules = indexOf(advisors, advisor -> advisor instanceof RulesAdvisor);
      int validation =
          indexOf(advisors, advisor -> isA(advisor.getAdvice(), VALIDATION_INTERCEPTOR));
      if (rules >= 0 && validation > rules) {
        var advisor = advisors[validation];
        advised.removeAdvisor(validation);
        advised.addAdvisor(
            rules > 0 && isA(advisors[rules - 1], TRANSACTION_ADVISOR) ? rules - 1 : rules,
            advisor);
      }
    }
    return bean;
  }

  private static int indexOf(Advisor[] advisors, Predicate<Advisor> wanted) {
    for (int i = 0; i < advisors.length; i++) {
      if (wanted.test(advisors[i])) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Returns whether the object is of the named class or a subclass. The class is named, not loaded:
   * validation and transactions may be absent from the application.
   */
  private static boolean isA(Object object, String className) {
    for (@Nullable Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
      if (type.getName().equals(className)) {
        return true;
      }
    }
    return false;
  }
}
