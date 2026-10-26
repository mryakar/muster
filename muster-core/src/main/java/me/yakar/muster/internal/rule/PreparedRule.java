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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import me.yakar.muster.rule.BusinessRule;
import me.yakar.muster.rule.RuleContext;

/**
 * A rule definition with the instance that checks it, and a context to start each call's context
 * from. Chains share a prepared rule across threads: everything is final, and the only state that
 * changes, whether the rule has been told that it checks elements one by one, is atomic.
 */
public final class PreparedRule {

  private static final Class<?>[] CHECK_ALL_PARAMETERS = {List.class, RuleContext.class};

  private final RuleDefinition definition;
  private final BusinessRule<Object> instance;
  private final RuleContext context;
  private final boolean checksAll;
  private final AtomicBoolean toldOneByOne = new AtomicBoolean();

  /**
   * Prepares a rule. Checks that the context is the rule's own and has no caller: every call
   * derives its context from it, and checks violations against the rule's id.
   *
   * @param definition the definition
   * @param instance the instance; it accepts inputs of the {@linkplain RuleDefinition#inputType()
   *     input type} only
   * @param context the rule's context with no caller and the root locale; its id is checked once,
   *     here, rather than on every call
   * @throws IllegalArgumentException if the context is of another rule or has a caller
   */
  public PreparedRule(
      RuleDefinition definition, BusinessRule<Object> instance, RuleContext context) {
    if (!context.ruleId().equals(definition.id()) || context.caller().isPresent()) {
      throw new IllegalArgumentException(
          "the prepared context of rule '"
              + definition.id()
              + "' must be for that rule and have no caller");
    }
    this.definition = definition;
    this.instance = instance;
    this.context = context;
    this.checksAll = overridesCheckAll(definition.ruleClass());
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
   * Returns the definition.
   *
   * @return the definition
   */
  public RuleDefinition definition() {
    return definition;
  }

  /**
   * Returns the instance.
   *
   * @return the instance
   */
  public BusinessRule<Object> instance() {
    return instance;
  }

  /**
   * Returns the context to start each call's context from.
   *
   * @return the context
   */
  public RuleContext context() {
    return context;
  }

  /**
   * Returns the rule id.
   *
   * @return the rule id
   */
  public String id() {
    return definition.id();
  }

  /**
   * Returns whether the rule checks a collection in {@code checkAll} of its own. Otherwise Muster
   * checks the elements one by one itself, so that it can stop at the first violated element.
   *
   * @return whether the rule class overrides {@code checkAll}
   */
  public boolean checksAll() {
    return checksAll;
  }

  /**
   * Returns {@code true} the first time it is called, and {@code false} after that, also when
   * threads call it at once: the rule is told once that it checks elements one by one.
   *
   * @return whether this is the first call
   */
  public boolean firstOneByOne() {
    return toldOneByOne.compareAndSet(false, true);
  }

  @Override
  public String toString() {
    return "PreparedRule[" + definition.id() + "]";
  }

  /**
   * Returns whether a rule class overrides {@code checkAll}. The public methods of a class have the
   * default method of {@code BusinessRule} unless the class, or a superclass, overrides it.
   */
  private static boolean overridesCheckAll(Class<?> ruleClass) {
    return Arrays.stream(ruleClass.getMethods())
        .filter(method -> method.getName().equals("checkAll"))
        .filter(method -> Arrays.equals(method.getParameterTypes(), CHECK_ALL_PARAMETERS))
        .anyMatch(method -> method.getDeclaringClass() != BusinessRule.class);
  }
}
