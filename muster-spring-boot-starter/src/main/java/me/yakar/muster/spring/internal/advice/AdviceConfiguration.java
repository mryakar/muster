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

import me.yakar.muster.Muster;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Role;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;

/**
 * Checks the rules of advised methods, inside their transaction and after the constraints of the
 * method. Not annotated with {@code Configuration}, so that component scanning does not find it;
 * Muster's auto-configuration imports it.
 */
@Import({ProxyCreatorRegistrar.class, AdviceConfiguration.TransactionConfiguration.class})
public final class AdviceConfiguration {

  AdviceConfiguration() {}

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  static RulesInterceptor musterRulesInterceptor(
      ObjectProvider<Muster> muster, ObjectProvider<TransactionGuard> transactions) {
    return new RulesInterceptor(muster, transactions);
  }

  // Infrastructure, so that the proxy creator that transaction management registers applies it.
  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  static RulesAdvisor musterRulesAdvisor(RulesInterceptor musterRulesInterceptor) {
    return new RulesAdvisor(musterRulesInterceptor);
  }

  @Bean
  static ValidationOrdering musterValidationOrdering() {
    return new ValidationOrdering();
  }

  /** Keeps rules inside the transaction when the application has Spring's transaction support. */
  @ConditionalOnClass(
      name =
          "org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor")
  static class TransactionConfiguration {

    @Bean
    static TransactionOrdering musterTransactionOrdering() {
      return new TransactionOrdering();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static TransactionGuard musterTransactionGuard(
        ObjectProvider<BeanFactoryTransactionAttributeSourceAdvisor> advisor) {
      return new TransactionGuard(advisor);
    }
  }
}
