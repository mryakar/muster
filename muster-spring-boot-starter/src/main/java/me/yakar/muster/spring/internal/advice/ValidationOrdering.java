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

import static java.util.Objects.requireNonNull;

import me.yakar.muster.spring.internal.Settings;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * Moves the method validation advice ahead of Muster's, so that the constraints of a method are
 * checked before its rules, and before its transaction.
 *
 * <p>Spring Boot's post-processor adds the validation advice to a proxy after the advice the proxy
 * already has, so the rules of a method would run before its constraints and see an input that
 * validation rejects. Before any bean is created, this tells the definition of the post-processor
 * to add its advice before the existing ones, unless the application defines the post-processor
 * itself, or {@code muster.validation.reorder-advisor} is {@code false}. Without method validation,
 * there is nothing to move.
 */
final class ValidationOrdering implements BeanFactoryPostProcessor, EnvironmentAware {

  /** The name of the post-processor that Spring Boot defines. */
  static final String POST_PROCESSOR = "methodValidationPostProcessor";

  private static final String SPRING_BOOT_CONFIGURATION =
      "org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration";
  private static final String BEFORE_EXISTING_ADVISORS = "beforeExistingAdvisors";

  // Set through Aware rather than injected: resolving a parameter of the bean method by type would
  // create factory beans early to learn what they make.
  private @Nullable Environment environment;

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    if (!Settings.reorderValidationAdvisor(requireNonNull(environment))
        || !beanFactory.containsBeanDefinition(POST_PROCESSOR)) {
      return;
    }
    var definition = beanFactory.getBeanDefinition(POST_PROCESSOR);
    // A post-processor of the application's own, which Spring Boot then does not define, keeps
    // the order the application gives it, as does a property set on the definition.
    if (definition instanceof AnnotatedBeanDefinition annotated
        && annotated.getFactoryMethodMetadata() != null
        && SPRING_BOOT_CONFIGURATION.equals(
            annotated.getFactoryMethodMetadata().getDeclaringClassName())
        && !definition.getPropertyValues().contains(BEFORE_EXISTING_ADVISORS)) {
      definition.getPropertyValues().add(BEFORE_EXISTING_ADVISORS, true);
    }
  }
}
