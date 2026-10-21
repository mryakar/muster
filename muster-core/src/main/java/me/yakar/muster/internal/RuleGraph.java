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

import static java.util.Comparator.comparing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Checks and orders the rules that require other rules. */
public final class RuleGraph {

  private RuleGraph() {}

  /**
   * Checks that every required rule is registered, accepts the input of the rule that requires it,
   * and is not disabled while the rule that requires it is enabled, and that no rules require each
   * other in a cycle.
   *
   * @param rules the registered rules, by class
   * @param disabled the classes of the disabled rules
   * @throws IllegalArgumentException if a requirement is not met
   */
  public static void validate(Map<Class<?>, RuleDefinition> rules, Set<Class<?>> disabled) {
    for (var rule : rules.values()) {
      for (var requiredClass : rule.requires()) {
        var required = rules.get(requiredClass);
        if (required == null) {
          throw new IllegalArgumentException(
              rule.ruleClass().getTypeName()
                  + " requires "
                  + requiredClass.getTypeName()
                  + ", which is not registered");
        }
        if (!required.inputType().isAssignableFrom(rule.inputType())) {
          throw new IllegalArgumentException(
              rule.ruleClass().getTypeName()
                  + " requires "
                  + requiredClass.getTypeName()
                  + ", which checks "
                  + required.inputType().getTypeName()
                  + ", not "
                  + rule.inputType().getTypeName());
        }
        if (disabled.contains(requiredClass) && !disabled.contains(rule.ruleClass())) {
          throw new IllegalArgumentException(
              "'"
                  + rule.id()
                  + "' requires '"
                  + required.id()
                  + "', which is disabled; disable '"
                  + rule.id()
                  + "' as well, or enable '"
                  + required.id()
                  + "'");
        }
      }
    }
    var finished = new HashSet<Class<?>>();
    for (var rule : byId(rules.values())) {
      requireNoCycle(rule, rules, finished, new ArrayList<>());
    }
  }

  private static void requireNoCycle(
      RuleDefinition rule,
      Map<Class<?>, RuleDefinition> rules,
      Set<Class<?>> finished,
      List<RuleDefinition> path) {
    if (finished.contains(rule.ruleClass())) {
      return;
    }
    var start = path.indexOf(rule);
    if (start >= 0) {
      throw new IllegalArgumentException(
          "rules require each other in a cycle: "
              + Stream.concat(path.subList(start, path.size()).stream(), Stream.of(rule))
                  .map(RuleDefinition::id)
                  .collect(Collectors.joining(" → ")));
    }
    path.add(rule);
    for (var required : rule.requires()) {
      requireNoCycle(rules.get(required), rules, finished, path);
    }
    path.removeLast();
    finished.add(rule.ruleClass());
  }

  /**
   * Orders rules so that each comes after the rules it requires; rules that do not depend on each
   * other come in the order of their ids. Every required rule must be among the given ones, and
   * there must be no cycle.
   *
   * @param rules the rules
   * @return the rules in order
   */
  public static List<RuleDefinition> order(Collection<RuleDefinition> rules) {
    var waitingFor = new HashMap<Class<?>, Integer>();
    var dependents = new HashMap<Class<?>, List<RuleDefinition>>();
    var ready = new PriorityQueue<RuleDefinition>(comparing(RuleDefinition::id));
    for (var rule : rules) {
      waitingFor.put(rule.ruleClass(), rule.requires().size());
      for (var required : rule.requires()) {
        dependents.computeIfAbsent(required, key -> new ArrayList<>()).add(rule);
      }
      if (rule.requires().isEmpty()) {
        ready.add(rule);
      }
    }
    var ordered = new ArrayList<RuleDefinition>();
    while (!ready.isEmpty()) {
      var rule = ready.remove();
      ordered.add(rule);
      for (var dependent : dependents.getOrDefault(rule.ruleClass(), List.of())) {
        if (waitingFor.merge(dependent.ruleClass(), -1, Integer::sum) == 0) {
          ready.add(dependent);
        }
      }
    }
    return ordered;
  }

  private static List<RuleDefinition> byId(Collection<RuleDefinition> rules) {
    var sorted = new ArrayList<>(rules);
    sorted.sort(comparing(RuleDefinition::id));
    return sorted;
  }
}
