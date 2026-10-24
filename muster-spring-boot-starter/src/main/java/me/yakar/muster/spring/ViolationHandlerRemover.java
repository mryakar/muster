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

import me.yakar.muster.BusinessRuleViolationException;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.HierarchicalBeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

/**
 * Removes the starter's handler of violations if the application has a controller advice that names
 * {@link BusinessRuleViolationException} in an {@link
 * org.springframework.web.bind.annotation.ExceptionHandler ExceptionHandler}, in its own context or
 * in a parent one, where Spring finds controller advice too. A handler of a supertype, such as
 * {@link Exception}, does not count: it is meant for errors, not for violations.
 *
 * <p>It runs once every configuration class, auto-configurations included, has registered its
 * beans, so an advice that an auto-configuration after Muster's registers counts as well. It reads
 * bean types only and creates no bean.
 */
final class ViolationHandlerRemover implements BeanDefinitionRegistryPostProcessor {

  /** The name of the bean that answers violations with problem details. */
  static final String HANDLER = "musterViolationExceptionHandler";

  @Override
  public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
    BeanFactory beanFactory = (BeanFactory) registry;
    while (beanFactory instanceof ListableBeanFactory listable) {
      for (var name : listable.getBeanDefinitionNames()) {
        if (!name.equals(HANDLER) && handlesViolations(listable.getType(name, false))) {
          registry.removeBeanDefinition(HANDLER);
          return;
        }
      }
      beanFactory =
          beanFactory instanceof HierarchicalBeanFactory hierarchical
              ? hierarchical.getParentBeanFactory()
              : null;
    }
  }

  private static boolean handlesViolations(@Nullable Class<?> type) {
    if (type == null || !AnnotatedElementUtils.hasAnnotation(type, ControllerAdvice.class)) {
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
