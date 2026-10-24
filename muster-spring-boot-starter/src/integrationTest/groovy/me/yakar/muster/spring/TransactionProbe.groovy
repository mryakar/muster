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
package me.yakar.muster.spring

import java.util.concurrent.CopyOnWriteArrayList
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import org.springframework.transaction.support.TransactionSynchronizationManager

/** A call to probe. */
record ProbeCall(String text) {}

/** Notes whether a transaction is active while it runs. */
@Rule(id = 'probe.transaction')
class TransactionProbe implements BusinessRule<ProbeCall> {
  static final List<Boolean> IN_TRANSACTION = new CopyOnWriteArrayList<>()

  RuleResult check(ProbeCall input, RuleContext ctx) {
    IN_TRANSACTION << TransactionSynchronizationManager.actualTransactionActive
    RuleResult.passed()
  }
}
