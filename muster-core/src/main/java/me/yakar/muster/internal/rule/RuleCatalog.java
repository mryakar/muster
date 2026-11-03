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

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * The rules registered with a {@code Muster}, and which of them are disabled. Immutable, so the
 * chains and bindings of the {@code Muster} share it across threads.
 */
public final class RuleCatalog {

  private final Map<Class<?>, PreparedRule> rules;
  private final Set<Class<?>> disabled;

  /**
   * Creates a catalog.
   *
   * @param rules the registered rules, by class
   * @param disabled the classes of the disabled rules
   */
  public RuleCatalog(Map<Class<?>, PreparedRule> rules, Set<Class<?>> disabled) {
    this.rules = Map.copyOf(rules);
    this.disabled = Set.copyOf(disabled);
  }

  /**
   * Returns a registered rule.
   *
   * @param ruleClass the rule class
   * @return the rule
   * @throws IllegalArgumentException if the rule is not registered
   */
  public PreparedRule registered(Class<?> ruleClass) {
    requireNonNull(ruleClass, "ruleClass");
    var rule = rules.get(ruleClass);
    if (rule == null) {
      throw new IllegalArgumentException(ruleClass.getTypeName() + " is not registered");
    }
    return rule;
  }

  /**
   * Checks that a rule listed for inputs of a type is registered and accepts them.
   *
   * @param ruleClass the rule class
   * @param inputType the type of input, as the caller gave it
   * @param wrapped the type of input, with a primitive type replaced by its wrapper
   * @throws IllegalArgumentException if the rule is not registered or does not accept the type
   */
  public void requireListable(Class<?> ruleClass, Class<?> inputType, Class<?> wrapped) {
    var rule = registered(ruleClass);
    var ruleInputType = rule.definition().inputType();
    if (!ruleInputType.isAssignableFrom(wrapped)) {
      throw new IllegalArgumentException(
          ruleClass.getTypeName()
              + " checks "
              + ruleInputType.getTypeName()
              + ", which "
              + inputType.getTypeName()
              + " is not");
    }
  }

  /**
   * Checks that a rule can be listed for a collection: it accepts its elements, or it checks whole
   * collections of them.
   *
   * @param ruleClass the rule class
   * @param elementType the element type, as the caller declared it
   * @param wrapped the element type, with primitives wrapped
   * @throws IllegalArgumentException if the rule is not registered or checks neither
   */
  public void requireListableInCollection(
      Class<?> ruleClass, Class<?> elementType, Class<?> wrapped) {
    var definition = registered(ruleClass).definition();
    if (definition.inputType().isAssignableFrom(wrapped)
        || definition.checksCollectionsOf(wrapped)) {
      return;
    }
    var elements = definition.elementType();
    throw new IllegalArgumentException(
        ruleClass.getTypeName()
            + " checks "
            + definition.inputType().getTypeName()
            + (elements == null ? "" : " of " + elements.getTypeName())
            + ", which neither "
            + elementType.getTypeName()
            + " nor a list of "
            + elementType.getTypeName()
            + " is; a rule of the whole collection takes a List, SequencedCollection or Collection"
            + " of its elements");
  }

  /**
   * Checks the listed and excluded rules of a binding: each listed rule passes the given check,
   * each excluded one is registered and not listed, and no enabled listed rule requires an excluded
   * one.
   *
   * @param listed the listed rules
   * @param excluded the excluded rules
   * @param requireListable checks a listed rule, and throws if it cannot be listed
   * @throws IllegalArgumentException if a rule fails one of the checks
   */
  public void requireSelectable(
      Set<Class<?>> listed, Set<Class<?>> excluded, Consumer<Class<?>> requireListable) {
    listed.forEach(requireListable);
    for (var ruleClass : excluded) {
      registered(ruleClass);
      if (listed.contains(ruleClass)) {
        throw new IllegalArgumentException(
            ruleClass.getTypeName() + " is both listed and excluded");
      }
    }
    var problems = new ArrayList<String>();
    for (var ruleClass : listed) {
      if (disabled.contains(ruleClass)) {
        continue;
      }
      var required = excludedRequirement(ruleClass, excluded);
      if (required != null) {
        problems.add(
            "'" + registered(ruleClass).id() + "' requires '" + registered(required).id() + "'");
      }
    }
    if (!problems.isEmpty()) {
      throw new IllegalArgumentException(
          "listed rules require excluded rules, directly or through other rules: "
              + String.join("; ", problems)
              + "; list other rules, or stop excluding what they require");
    }
  }

  /**
   * Returns the ids of registered rules, in the given order.
   *
   * @param ruleClasses the registered rules
   * @return the ids
   */
  public List<String> ids(Collection<Class<?>> ruleClasses) {
    return ruleClasses.stream().map(ruleClass -> registered(ruleClass).id()).toList();
  }

  /**
   * Returns the ids of the disabled rules, in the order of the ids.
   *
   * @return the ids, unmodifiable
   */
  public SortedSet<String> disabledIds() {
    var ids = new TreeSet<String>();
    for (var ruleClass : disabled) {
      ids.add(registered(ruleClass).id());
    }
    return Collections.unmodifiableSortedSet(ids);
  }

  /**
   * Returns the enabled ones of registered rules, in the given order, in a map the caller may add
   * to.
   *
   * @param ruleClasses the registered rules
   * @return the enabled rules, by class
   */
  LinkedHashMap<Class<?>, PreparedRule> enabled(Collection<Class<?>> ruleClasses) {
    var enabled = new LinkedHashMap<Class<?>, PreparedRule>();
    for (var ruleClass : ruleClasses) {
      if (!disabled.contains(ruleClass)) {
        enabled.put(ruleClass, rules.get(ruleClass));
      }
    }
    return enabled;
  }

  /**
   * Returns the enabled rules whose input type accepts a class, in no particular order.
   *
   * @param type the class
   * @return the rules
   */
  List<PreparedRule> accepting(Class<?> type) {
    return rules.values().stream()
        .filter(
            rule ->
                !disabled.contains(rule.definition().ruleClass())
                    && rule.definition().inputType().isAssignableFrom(type))
        .toList();
  }

  /**
   * Returns the enabled rules that check whole collections of the given class of elements.
   *
   * @param elementClass the class of the elements
   * @return the rules
   */
  List<PreparedRule> checkingCollectionsOf(Class<?> elementClass) {
    return rules.values().stream()
        .filter(
            rule ->
                !disabled.contains(rule.definition().ruleClass())
                    && rule.definition().checksCollectionsOf(elementClass))
        .toList();
  }

  /**
   * Returns an excluded rule that a registered rule requires, directly or through other rules, or
   * {@code null} if it requires none. With nothing excluded, it searches nothing.
   *
   * @param ruleClass the rule class
   * @param excluded the classes of the excluded rules
   * @return the first excluded rule found, or {@code null}
   */
  @Nullable Class<?> excludedRequirement(Class<?> ruleClass, Set<Class<?>> excluded) {
    if (excluded.isEmpty()) {
      return null;
    }
    return RuleGraph.firstRequired(
        requireNonNull(rules.get(ruleClass)).definition(),
        excluded::contains,
        required -> requireNonNull(rules.get(required)).definition());
  }
}
