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

import static java.util.stream.Collectors.joining;

import java.lang.System.Logger.Level;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.yakar.muster.rule.RuleTarget;
import org.jspecify.annotations.Nullable;

/**
 * The steps of a chain, in the order they run, and what composing them has to log. Composing logs
 * nothing itself, so that a cache can compose inside {@code computeIfAbsent} and log outside it.
 */
public final class ChainPlan {

  private static final System.Logger LOGGER = System.getLogger("me.yakar.muster");

  private final String label;
  private final List<Step> steps;
  private final List<String> notes;
  private final boolean bound;

  private ChainPlan(String label, List<Step> steps, List<String> notes, boolean bound) {
    this.label = label;
    this.steps = steps;
    this.notes = List.copyOf(notes);
    this.bound = bound;
  }

  /**
   * Plans the chain of listed rules: the enabled ones and the rules they require.
   *
   * @param catalog the registered rules
   * @param label names the chain in logs
   * @param listed the listed rules, registered and in the order they should run
   * @return the plan
   */
  public static ChainPlan listed(RuleCatalog catalog, String label, Collection<Class<?>> listed) {
    var included = catalog.enabled(listed);
    var notes = new ArrayList<String>();
    var steps = compose(catalog, label, included, List.copyOf(included.keySet()), notes, null);
    return new ChainPlan(label, steps, notes, false);
  }

  /**
   * Plans the chain of a class: the enabled rules a selection lists, and for a rule target the
   * enabled rules that bind to it by type, less the excluded ones and those that require them.
   *
   * @param catalog the registered rules
   * @param type the class
   * @param selection the listed and excluded rules
   * @return the plan
   */
  public static ChainPlan bound(RuleCatalog catalog, Class<?> type, Selection selection) {
    var name = selection.name();
    var label =
        name == null || name.equals(type.getTypeName())
            ? type.getTypeName()
            : name + " (" + type.getTypeName() + ")";
    var included = catalog.enabled(selection.listed());
    var listedOrder = List.copyOf(included.keySet());
    var notes = new ArrayList<String>();
    if (RuleTarget.class.isAssignableFrom(type)) {
      addBound(catalog, catalog.accepting(type), selection, included, label, notes);
    }
    var steps = compose(catalog, label, included, listedOrder, notes, null);
    return new ChainPlan(label, steps, notes, true);
  }

  /**
   * Plans the chain that checks collections of a class: the listed rules and, for a {@link
   * RuleTarget}, the rules bound by type, both those that check each element and those that check
   * the whole collection, less the excluded ones. A rule that checks whole collections of the class
   * checks the collection; any other rule checks each element.
   *
   * @param catalog the registered rules
   * @param elementClass the class of the elements
   * @param selection the listed and excluded rules, and the name of the binding
   * @return the plan
   */
  public static ChainPlan collection(
      RuleCatalog catalog, Class<?> elementClass, Selection selection) {
    var collection = "a collection of " + elementClass.getTypeName();
    var name = selection.name();
    var label =
        name == null || name.equals(elementClass.getTypeName())
            ? collection
            : name + " (" + collection + ")";
    var included = catalog.enabled(selection.listed());
    var listedOrder = List.copyOf(included.keySet());
    var notes = new ArrayList<String>();
    if (RuleTarget.class.isAssignableFrom(elementClass)) {
      var candidates = new ArrayList<>(catalog.accepting(elementClass));
      candidates.addAll(catalog.checkingCollectionsOf(elementClass));
      addBound(catalog, candidates, selection, included, label, notes);
    }
    var steps = compose(catalog, label, included, listedOrder, notes, elementClass);
    return new ChainPlan(label, steps, notes, true);
  }

  /**
   * Adds the rules bound by type that are neither excluded nor listed, and notes those left out
   * because they require an excluded rule.
   */
  private static void addBound(
      RuleCatalog catalog,
      List<PreparedRule> candidates,
      Selection selection,
      Map<Class<?>, PreparedRule> included,
      String label,
      List<String> notes) {
    var excluded = selection.excluded();
    var leftOut = new ArrayList<String>();
    for (var rule : candidates) {
      var ruleClass = rule.definition().ruleClass();
      if (excluded.contains(ruleClass) || included.containsKey(ruleClass)) {
        continue;
      }
      var blocking = catalog.excludedRequirement(ruleClass, excluded);
      if (blocking == null) {
        included.put(ruleClass, rule);
      } else {
        leftOut.add("'" + rule.id() + "' requires '" + catalog.registered(blocking).id() + "'");
      }
    }
    if (!leftOut.isEmpty()) {
      notes.add(
          "The chain for "
              + label
              + " leaves out rules that require excluded rules: "
              + leftOut.stream().sorted().collect(joining("; ")));
    }
  }

  /** Adds the rules that the included ones require and orders them all, noting each one it adds. */
  private static List<Step> compose(
      RuleCatalog catalog,
      String label,
      Map<Class<?>, PreparedRule> included,
      List<Class<?>> listed,
      List<String> notes,
      @Nullable Class<?> elementClass) {
    var pending = new ArrayDeque<>(included.values());
    while (!pending.isEmpty()) {
      var rule = pending.removeFirst();
      for (var requiredClass : rule.definition().requires()) {
        if (!included.containsKey(requiredClass)) {
          var required = catalog.registered(requiredClass);
          included.put(requiredClass, required);
          pending.addLast(required);
          notes.add(
              "The chain for "
                  + label
                  + " includes rule '"
                  + required.id()
                  + "' because '"
                  + rule.id()
                  + "' requires it");
        }
      }
    }
    var ordered =
        RuleGraph.order(included.values().stream().map(PreparedRule::definition).toList(), listed);
    var positions = new HashMap<Class<?>, Integer>();
    var steps = new ArrayList<Step>();
    for (var definition : ordered) {
      var requires = definition.requires().stream().mapToInt(positions::get).toArray();
      positions.put(definition.ruleClass(), steps.size());
      var whole = elementClass != null && definition.checksCollectionsOf(elementClass);
      // A rule that a rule of the whole collection requires may accept neither, as one of Iterable
      // input does.
      if (elementClass != null
          && !whole
          && !definition.inputType().isAssignableFrom(elementClass)) {
        throw new IllegalArgumentException(
            "the chain for "
                + label
                + " cannot check rule '"
                + definition.id()
                + "', which checks "
                + definition.inputType().getTypeName()
                + ": neither "
                + elementClass.getTypeName()
                + " nor a list of "
                + elementClass.getTypeName()
                + " is one; a rule of the whole collection checks a list, so what it requires"
                + " checks lists or each element");
      }
      steps.add(new Step(included.get(definition.ruleClass()), requires, whole));
    }
    return List.copyOf(steps);
  }

  /**
   * Returns the steps, in the order they run.
   *
   * @return the steps
   */
  public List<Step> steps() {
    return steps;
  }

  /**
   * Logs the rules the chain adds and leaves out, and for a bound chain its rules, or a warning
   * that it has none.
   */
  public void log() {
    for (var note : notes) {
      LOGGER.log(Level.INFO, note);
    }
    if (!bound) {
      return;
    }
    if (steps.isEmpty()) {
      LOGGER.log(
          Level.WARNING,
          "No rule binds to "
              + label
              + "; check that its rules are registered, enabled and accept it");
    } else {
      LOGGER.log(
          Level.DEBUG,
          () ->
              "Rules bound to "
                  + label
                  + ": "
                  + steps.stream().map(step -> step.rule().id()).toList());
    }
  }

  /**
   * The rules a binding lists and excludes, and the name it logs under.
   *
   * @param listed the listed rules, registered and in the order they should run
   * @param excluded the excluded rules, registered
   * @param name the name of the binding, or {@code null} to name a chain after its class
   */
  public record Selection(List<Class<?>> listed, Set<Class<?>> excluded, @Nullable String name) {

    /** No listed and no excluded rules, as for {@code Muster.check}. */
    public static final Selection NONE = new Selection(List.of(), Set.of(), null);
  }
}
