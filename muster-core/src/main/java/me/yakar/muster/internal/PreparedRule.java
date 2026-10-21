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
package me.yakar.muster.internal;

import me.yakar.muster.rule.BusinessRule;

/**
 * A rule definition with the instance that checks it.
 *
 * @param definition the definition
 * @param rule the instance; it accepts inputs of the {@linkplain RuleDefinition#inputType() input
 *     type} only
 */
public record PreparedRule(RuleDefinition definition, BusinessRule<Object> rule) {

  /**
   * Pairs a definition with its instance.
   *
   * @param definition the definition
   * @param rule an instance of the defined class
   * @return the prepared rule
   */
  @SuppressWarnings("unchecked") // Callers pass inputs of the definition's input type only.
  public static PreparedRule of(RuleDefinition definition, BusinessRule<?> rule) {
    return new PreparedRule(definition, (BusinessRule<Object>) rule);
  }

  /**
   * Returns the rule id.
   *
   * @return the rule id
   */
  public String id() {
    return definition.id();
  }
}
