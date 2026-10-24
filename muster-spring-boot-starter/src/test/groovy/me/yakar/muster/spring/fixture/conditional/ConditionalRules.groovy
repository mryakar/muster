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
package me.yakar.muster.spring.fixture.conditional

import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import me.yakar.muster.spring.fixture.app.Names
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

/** A rule that exists only when a property says so. */
@Rule(id = 'conditional.on-property')
@ConditionalOnProperty('fixture.rule.enabled')
class OnProperty implements BusinessRule<Object> {
  RuleResult check(Object input, RuleContext ctx) {
    RuleResult.passed()
  }
}

/** A rule that exists only when the application has its dependency. */
@Rule(id = 'conditional.on-bean')
@ConditionalOnBean(Names)
class OnBean implements BusinessRule<Object> {
  RuleResult check(Object input, RuleContext ctx) {
    RuleResult.passed()
  }
}
