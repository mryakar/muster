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
package me.yakar.muster.rule;

import static me.yakar.muster.rule.Checks.requireNonBlank;

/**
 * What a rule knows about the call it checks, besides its input.
 *
 * <p>Each rule receives its own context. Rules create their violations through it, so every
 * violation carries the id of the rule that reported it.
 */
public final class RuleContext {

  private final String ruleId;

  private RuleContext(String ruleId) {
    this.ruleId = requireNonBlank(ruleId, "ruleId");
  }

  /**
   * Creates a context for the rule with the given id. Use it to test a rule on its own.
   *
   * @param ruleId the id of the rule being checked
   * @return the context
   * @throws IllegalArgumentException if {@code ruleId} is blank
   */
  public static RuleContext forRule(String ruleId) {
    return new RuleContext(ruleId);
  }

  /**
   * Returns the id of the rule being checked.
   *
   * @return the rule id
   */
  public String ruleId() {
    return ruleId;
  }

  /**
   * Starts a violation whose code is the rule id.
   *
   * @return a builder for the violation
   */
  public Violation.Builder violation() {
    return new Violation.Builder(ruleId, ruleId);
  }

  /**
   * Starts a violation with the given code. Use it when a rule reports more than one kind of
   * problem.
   *
   * @param code the stable, machine-readable code of the violation
   * @return a builder for the violation
   * @throws IllegalArgumentException if {@code code} is blank
   */
  public Violation.Builder violation(String code) {
    return new Violation.Builder(ruleId, code);
  }
}
