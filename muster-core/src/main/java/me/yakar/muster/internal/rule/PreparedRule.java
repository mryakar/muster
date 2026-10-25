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
package me.yakar.muster.internal.rule;

import me.yakar.muster.rule.BusinessRule;
import me.yakar.muster.rule.RuleContext;

/**
 * A rule definition with the instance that checks it, and a context to start each call's context
 * from. Every component is immutable, so chains share a prepared rule across threads.
 *
 * @param definition the definition
 * @param rule the instance; it accepts inputs of the {@linkplain RuleDefinition#inputType() input
 *     type} only
 * @param context the rule's context with no caller and the root locale; its id is checked once,
 *     here, rather than on every call
 */
public record PreparedRule(
    RuleDefinition definition, BusinessRule<Object> rule, RuleContext context) {

  /**
   * Checks that the context is the rule's own and has no caller: every call derives its context
   * from it, and checks violations against the rule's id.
   *
   * @param definition the definition
   * @param rule the instance
   * @param context the rule's context
   * @throws IllegalArgumentException if the context is of another rule or has a caller
   */
  public PreparedRule {
    if (!context.ruleId().equals(definition.id()) || context.caller().isPresent()) {
      throw new IllegalArgumentException(
          "the prepared context of rule '"
              + definition.id()
              + "' must be for that rule and have no caller");
    }
  }

  /**
   * Pairs a definition with its instance.
   *
   * @param definition the definition
   * @param rule an instance of the defined class
   * @return the prepared rule
   */
  @SuppressWarnings("unchecked") // Callers pass inputs of the definition's input type only.
  public static PreparedRule of(RuleDefinition definition, BusinessRule<?> rule) {
    return new PreparedRule(
        definition, (BusinessRule<Object>) rule, RuleContext.forRule(definition.id()));
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
