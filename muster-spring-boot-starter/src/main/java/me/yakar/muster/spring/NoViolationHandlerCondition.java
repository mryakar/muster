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
package me.yakar.muster.spring;

import static java.util.Objects.requireNonNull;

import me.yakar.muster.BusinessRuleViolationException;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.http.MediaType;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

/**
 * Matches when no controller advice of the application names {@link BusinessRuleViolationException}
 * in an {@link org.springframework.web.bind.annotation.ExceptionHandler ExceptionHandler}. A
 * handler of a supertype, such as {@link Exception}, does not count: it is meant for errors, not
 * for violations.
 *
 * <p>It reads the bean definitions, so it must decide on a bean method, once the configuration of
 * the application has registered its beans.
 */
final class NoViolationHandlerCondition extends SpringBootCondition {

  @Override
  public ConditionOutcome getMatchOutcome(
      ConditionContext context, AnnotatedTypeMetadata metadata) {
    var beanFactory = requireNonNull(context.getBeanFactory(), "beanFactory");
    for (var name : beanFactory.getBeanDefinitionNames()) {
      var type = beanFactory.getType(name, false);
      if (type != null && handlesViolations(ClassUtils.getUserClass(type))) {
        return ConditionOutcome.noMatch(
            "the controller advice '" + name + "' handles " + BusinessRuleViolationException.class);
      }
    }
    return ConditionOutcome.match(
        "no controller advice handles " + BusinessRuleViolationException.class);
  }

  private static boolean handlesViolations(Class<?> type) {
    if (!AnnotatedElementUtils.hasAnnotation(type, ControllerAdvice.class)) {
      return false;
    }
    var mapping =
        new ExceptionHandlerMethodResolver(type)
            .resolveExceptionMappingByExceptionType(
                BusinessRuleViolationException.class, MediaType.ALL);
    return mapping != null
        && mapping.getExceptionTypes().contains(BusinessRuleViolationException.class);
  }
}
