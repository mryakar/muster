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
package me.yakar.muster.spring.fixture.eager

import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import me.yakar.muster.spring.fixture.lazy.Plates

/** A rule whose constructor calls an advised service, while Muster is created. */
@Rule(id = 'eager.calls-service')
class CallsServiceWhenCreated implements BusinessRule<Long> {
  CallsServiceWhenCreated(Plates plates) {
    plates.known('TR-1')
  }

  RuleResult check(Long input, RuleContext ctx) {
    RuleResult.passed()
  }
}
