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

import java.util.ArrayList;
import me.yakar.muster.spring.internal.Settings;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.config.TransactionManagementConfigUtils;

/**
 * Moves the transaction advice one step ahead of Muster's, so that rules run inside the
 * transaction.
 *
 * <p>Both advisors have the lowest precedence by default, and with equal orders the one registered
 * first runs outside, which depends on how the application is configured. Before any bean is
 * created, this sets the order of the transaction advisor's definition to one less, unless the
 * application gave the transaction advice an order of its own, or {@code
 * muster.transaction.reorder-advisor} is {@code false}. Muster checks the outcome once the
 * application has started. Transactions woven in with AspectJ cannot run around a proxy's advice,
 * so they stop the application.
 */
final class TransactionOrdering implements BeanFactoryPostProcessor, EnvironmentAware {

  // Set through Aware rather than injected: resolving a parameter of the bean method by type would
  // create factory beans early to learn what they make.
  private @Nullable Environment environment;

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    if (beanFactory.containsBeanDefinition(
        TransactionManagementConfigUtils.TRANSACTION_ASPECT_BEAN_NAME)) {
      throw new IllegalStateException(
          "transactions are woven in with AspectJ, so they cannot run around Muster's advice, and"
              + " rules would run outside them; use @EnableTransactionManagement in proxy mode");
    }
    var advisor = TransactionManagementConfigUtils.TRANSACTION_ADVISOR_BEAN_NAME;
    if (!Settings.reorderAdvisor(requireNonNull(environment))
        || !beanFactory.containsBeanDefinition(advisor)
        // An order set on the definition, as with <tx:annotation-driven order="...">, is the
        // application's.
        || beanFactory.getBeanDefinition(advisor).getPropertyValues().contains("order")) {
      return;
    }
    var orders = new ArrayList<Object>();
    for (var name : beanFactory.getBeanDefinitionNames()) {
      if (beanFactory.getBeanDefinition(name) instanceof AnnotatedBeanDefinition definition) {
        var attributes =
            definition
                .getMetadata()
                .getAnnotationAttributes(EnableTransactionManagement.class.getName());
        if (attributes != null) {
          orders.add(attributes.get("order"));
        }
      }
    }
    // An order given as the lowest precedence cannot be told from the default.
    if (orders.stream().allMatch(order -> order.equals(Ordered.LOWEST_PRECEDENCE))) {
      beanFactory
          .getBeanDefinition(advisor)
          .getPropertyValues()
          .add("order", Ordered.LOWEST_PRECEDENCE - 1);
    }
  }
}
