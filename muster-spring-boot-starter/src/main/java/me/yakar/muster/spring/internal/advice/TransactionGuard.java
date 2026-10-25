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

import java.lang.reflect.Method;
import java.util.Set;
import java.util.function.BiPredicate;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Makes sure that rules run inside the transaction of their method. Used only when Spring's
 * transaction support is on the class path, and only once the application has a transaction
 * advisor.
 */
final class TransactionGuard {

  /** The propagations that run a method in a transaction, whether or not one exists. */
  private static final Set<Integer> REQUIRING =
      Set.of(
          TransactionDefinition.PROPAGATION_REQUIRED,
          TransactionDefinition.PROPAGATION_REQUIRES_NEW,
          TransactionDefinition.PROPAGATION_MANDATORY,
          TransactionDefinition.PROPAGATION_NESTED);

  private final ObjectProvider<BeanFactoryTransactionAttributeSourceAdvisor> advisor;

  TransactionGuard(ObjectProvider<BeanFactoryTransactionAttributeSourceAdvisor> advisor) {
    this.advisor = advisor;
  }

  /**
   * Returns whether a method must run in a transaction that the transaction advice starts, or
   * {@code null} if the application has no transaction advice. The test keeps the types of Spring's
   * transactions out of the classes that use it, which load without them.
   */
  @Nullable BiPredicate<Method, Class<?>> transactional() {
    var transactionAdvisor = advisor.getIfUnique();
    if (transactionAdvisor == null) {
      return null;
    }
    var attributes =
        requireNonNull(
            ((TransactionInterceptor) transactionAdvisor.getAdvice())
                .getTransactionAttributeSource());
    return (method, targetClass) -> {
      var attribute = attributes.getTransactionAttribute(method, targetClass);
      return attribute != null && REQUIRING.contains(attribute.getPropagationBehavior());
    };
  }

  /**
   * Fails a call whose method requires a transaction when none is active, which means that the
   * transaction advice runs inside Muster's rather than around it.
   */
  void requireTransaction(String label) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          label
              + " runs in a transaction, but its rules ran with none active; the transaction advice"
              + " must run around Muster's");
    }
  }

  /** Fails the start of the application if the transaction advice would not run around Muster's. */
  void checkOrder(int musterOrder) {
    var transactionAdvisor = advisor.getIfUnique();
    if (transactionAdvisor != null && transactionAdvisor.getOrder() >= musterOrder) {
      throw new IllegalStateException(
          "the transaction advice has the order "
              + transactionAdvisor.getOrder()
              + ", which does not come before Muster's, "
              + musterOrder
              + ", so rules could run outside the transaction; let Muster move it, with the order"
              + " of @EnableTransactionManagement at its default and"
              + " muster.transaction.reorder-advisor on, or give it a lower order");
    }
  }
}
