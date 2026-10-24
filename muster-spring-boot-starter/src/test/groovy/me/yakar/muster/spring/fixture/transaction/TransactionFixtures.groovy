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
package me.yakar.muster.spring.fixture.transaction

import java.util.concurrent.CopyOnWriteArrayList
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import me.yakar.muster.spring.Rules
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager

/** Notes whether a transaction is active while it runs. */
@Rule(id = 'transaction.probe')
class TransactionProbe implements BusinessRule<String> {
  static final List<Boolean> IN_TRANSACTION = new CopyOnWriteArrayList<>()

  RuleResult check(String input, RuleContext ctx) {
    IN_TRANSACTION << TransactionSynchronizationManager.actualTransactionActive
    RuleResult.passed()
  }
}

/** Methods in and out of transactions. */
class TransactionalService {
  @Transactional
  @Rules(TransactionProbe)
  void required(String text) {}

  @Transactional(propagation = Propagation.SUPPORTS)
  @Rules(TransactionProbe)
  void supports(String text) {}

  @Rules(TransactionProbe)
  void plain(String text) {}
}

/** A transaction manager without resources, which can leave synchronization off. */
class FakeTransactionManager extends AbstractPlatformTransactionManager {
  FakeTransactionManager(int synchronization = SYNCHRONIZATION_ALWAYS) {
    transactionSynchronization = synchronization
  }

  Object doGetTransaction() {
    new Object()
  }

  void doBegin(Object transaction, TransactionDefinition definition) {}

  void doCommit(DefaultTransactionStatus status) {}

  void doRollback(DefaultTransactionStatus status) {}
}
