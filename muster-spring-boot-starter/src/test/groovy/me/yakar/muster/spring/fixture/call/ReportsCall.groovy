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
package me.yakar.muster.spring.fixture.call

import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import me.yakar.muster.rule.RuleTarget

/** A call to report. */
record Call(String text) implements RuleTarget {}

/** Fails every call, and reports its caller and locale. */
@Rule(id = 'call.reported')
class ReportsCall implements BusinessRule<Call> {
  RuleResult check(Call input, RuleContext ctx) {
    RuleResult.failed(ctx.violation()
        .clientArg('caller', ctx.caller().map { it.name }.orElse('none'))
        .clientArg('locale', ctx.locale().toLanguageTag())
        .build())
  }
}
