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
import java.util.Comparator;
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
   * Checks that every required rule is registered and accepts the input of the rule that requires
   * it, that no rules require each other in a cycle, and that no enabled rule requires a disabled
   * rule, directly or through other rules.
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
      }
    }
    var finished = new HashSet<Class<?>>();
    for (var rule : byId(rules.values())) {
      requireNoCycle(rule, rules, finished, new ArrayList<>());
    }
    requireDisabledDependents(rules, disabled);
  }

  /**
   * Fails if an enabled rule requires a disabled rule, directly or through other rules, and names
   * every such rule at once, so that one change to the configuration can fix them all.
   */
  private static void requireDisabledDependents(
      Map<Class<?>, RuleDefinition> rules, Set<Class<?>> disabled) {
    var problems = new ArrayList<String>();
    for (var off : byId(rules.values())) {
      if (!disabled.contains(off.ruleClass())) {
        continue;
      }
      var dependents = new ArrayList<String>();
      for (var rule : byId(rules.values())) {
        if (!disabled.contains(rule.ruleClass())
            && requires(rule, off.ruleClass(), rules, new HashSet<>())) {
          dependents.add("'" + rule.id() + "'");
        }
      }
      if (!dependents.isEmpty()) {
        problems.add(
            String.join(", ", dependents)
                + (dependents.size() == 1 ? " requires '" : " require '")
                + off.id()
                + "'");
      }
    }
    if (!problems.isEmpty()) {
      throw new IllegalArgumentException(
          "rules that require disabled rules are enabled: "
              + String.join("; ", problems)
              + "; disable them as well, or enable what they require");
    }
  }

  /** Whether a rule requires the given rule, directly or through other rules. */
  private static boolean requires(
      RuleDefinition rule,
      Class<?> required,
      Map<Class<?>, RuleDefinition> rules,
      Set<Class<?>> visited) {
    for (var direct : rule.requires()) {
      if (direct == required
          || (visited.add(direct) && requires(rules.get(direct), required, rules, visited))) {
        return true;
      }
    }
    return false;
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
                  .collect(Collectors.joining(" -> ")));
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
    return order(rules, List.of());
  }

  /**
   * Orders rules so that each comes after the rules it requires; otherwise listed rules come in the
   * order they are listed, and a rule that is not listed takes the place of the first listed rule
   * that requires it, directly or through other rules, so it comes just before that rule. Rules in
   * the same place, and rules that no listed rule requires, come in the order of their ids. Every
   * required rule must be among the given ones, and there must be no cycle.
   *
   * @param rules the rules
   * @param listed the classes of the listed rules, in the order they are listed
   * @return the rules in order
   */
  public static List<RuleDefinition> order(
      Collection<RuleDefinition> rules, List<Class<?>> listed) {
    var byClass = new HashMap<Class<?>, RuleDefinition>();
    for (var rule : rules) {
      byClass.put(rule.ruleClass(), rule);
    }
    var places = new HashMap<Class<?>, Integer>();
    for (var place = 0; place < listed.size(); place++) {
      assignPlace(listed.get(place), place, byClass, places);
    }
    var waitingFor = new HashMap<Class<?>, Integer>();
    var dependents = new HashMap<Class<?>, List<RuleDefinition>>();
    var ready =
        new PriorityQueue<RuleDefinition>(
            Comparator.<RuleDefinition>comparingInt(
                    rule -> places.getOrDefault(rule.ruleClass(), Integer.MAX_VALUE))
                .thenComparing(RuleDefinition::id));
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

  /** Gives a rule and the rules it requires, directly or not, a place unless they have one. */
  private static void assignPlace(
      Class<?> ruleClass,
      int place,
      Map<Class<?>, RuleDefinition> byClass,
      Map<Class<?>, Integer> places) {
    if (places.putIfAbsent(ruleClass, place) == null) {
      for (var required : byClass.get(ruleClass).requires()) {
        assignPlace(required, place, byClass, places);
      }
    }
  }

  private static List<RuleDefinition> byId(Collection<RuleDefinition> rules) {
    var sorted = new ArrayList<>(rules);
    sorted.sort(comparing(RuleDefinition::id));
    return sorted;
  }
}
