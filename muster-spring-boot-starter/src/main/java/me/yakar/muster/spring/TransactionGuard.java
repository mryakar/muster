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

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;

/**
 * Makes sure that rules run inside the transaction of their method. Used only when Spring's
 * transaction support is on the class path, and only once the application has a transaction
 * advisor.
 */
final class TransactionGuard {

  private final ObjectProvider<BeanFactoryTransactionAttributeSourceAdvisor> advisor;

  TransactionGuard(ObjectProvider<BeanFactoryTransactionAttributeSourceAdvisor> advisor) {
    this.advisor = advisor;
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
              + ", so rules could run outside the transaction; leave the order of"
              + " @EnableTransactionManagement at its default for Muster to move it, or give it a"
              + " lower one");
    }
  }
}
