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
package me.yakar.muster;

import static java.util.Objects.requireNonNull;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import me.yakar.muster.internal.rule.ChainPlan.Selection;
import me.yakar.muster.internal.rule.RuleCatalog;
import me.yakar.muster.rule.BusinessRule;
import me.yakar.muster.rule.RuleTarget;
import org.jspecify.annotations.Nullable;

/**
 * The rules an input is checked against: the rules this binding lists and, for a {@link
 * RuleTarget}, the rules that bind to its class by type, less the excluded ones. Get one from
 * {@link Muster#binding}; unlike a {@link RuleChain}, a binding binds rules by type.
 *
 * <p>The listed rules run first, in the order {@link RuleChain} describes. The rules bound by type
 * follow, each after the rules it requires, in the order of their ids. A rule bound by type that
 * requires an excluded rule is left out and logged. Bindings are thread-safe.
 *
 * @param <I> the type of input
 */
public final class Binding<I> {

  private final Muster muster;
  private final Class<?> inputType;
  private final Selection selection;
  private final CheckMode mode;

  /** The chain of the only class an input of a final type can have, or {@code null}. */
  private final @Nullable RuleChain<Object> only;

  /** The chains of the classes checked so far, for a type that is not final. */
  private final ClassChains chains = new ClassChains(this::bind);

  private Binding(Muster muster, Class<?> inputType, Selection selection, CheckMode mode) {
    this.muster = muster;
    this.inputType = inputType;
    this.selection = selection;
    this.mode = mode;
    if (Modifier.isFinal(inputType.getModifiers())) {
      var bound = bind(inputType);
      bound.plan().log();
      this.only = bound.chain();
    } else {
      this.only = null;
    }
  }

  /**
   * Checks an input against the rules of this binding, in its {@linkplain #mode() mode}.
   *
   * @param input the input
   * @throws BusinessRuleViolationException if a rule is violated
   * @throws IllegalArgumentException if {@code input} is not of the binding's type, which only
   *     unchecked code can pass
   */
  public void check(I input) {
    requireNonNull(input, "input");
    if (!inputType.isInstance(input)) {
      throw new IllegalArgumentException(
          "the binding checks "
              + inputType.getTypeName()
              + ", not "
              + input.getClass().getTypeName());
    }
    var chain = only;
    if (chain == null) {
      chain = chains.of(input.getClass());
    }
    chain.run(input, mode);
  }

  /**
   * Checks the elements of a collection against the rules of this binding, in its {@linkplain
   * #mode() mode}. Each rule checks the elements that passed the rules it requires, at once if it
   * overrides {@link me.yakar.muster.rule.BusinessRule#checkAll}; one violated element rejects the
   * whole call, and violations name the position of their element in their target, as in {@code
   * [3].name}. An empty collection passes. The rules check each element; a {@link
   * CollectionBinding} also checks rules of the whole collection.
   *
   * @param inputs the inputs; where the input type of this binding is a rule target that is not
   *     final, all of one class; rules see an unmodifiable copy, taken when the check starts
   * @throws BusinessRuleViolationException if a rule is violated
   * @throws NullPointerException if an element is {@code null}
   * @throws IllegalArgumentException if the elements are of different classes, or one is not of the
   *     binding's type, which only unchecked code can pass
   */
  public void checkAll(Collection<? extends I> inputs) {
    // Rules bind by type to rule targets only, so other elements may be of different classes.
    var elements =
        RuleChain.elements(
            inputs,
            inputType,
            only == null && RuleTarget.class.isAssignableFrom(inputType),
            "binding");
    if (elements.isEmpty()) {
      return;
    }
    var chain = only;
    if (chain == null) {
      chain = chains.of(elements.getFirst().getClass());
    }
    chain.runAll(elements, mode);
  }

  /**
   * Returns the mode this binding checks in: its own, or else the mode of the {@code Muster}.
   *
   * @return the mode
   */
  public CheckMode mode() {
    return mode;
  }

  /**
   * Describes this binding for developers and logs: its name, input type, mode, and the ids of its
   * listed and excluded rules.
   *
   * @return the description
   */
  @Override
  public String toString() {
    var catalog = muster.catalog();
    return "Binding[name="
        + selection.name()
        + ", input="
        + inputType.getTypeName()
        + ", mode="
        + mode
        + ", rules="
        + selection.listed().stream().map(ruleClass -> catalog.registered(ruleClass).id()).toList()
        + ", excluded="
        + selection.excluded().stream()
            .map(ruleClass -> catalog.registered(ruleClass).id())
            .sorted()
            .toList()
        + "]";
  }

  private ClassChains.Bound bind(Class<?> type) {
    return muster.bind(type, selection, mode);
  }

  /**
   * Builds a {@link Binding}. Get one from {@link Muster#binding}. A builder is not safe for use by
   * multiple threads.
   *
   * @param <I> the type of input
   */
  public static final class Builder<I> {

    private final Muster muster;
    private final Class<I> inputType;
    private final Set<Class<?>> listed = new LinkedHashSet<>();
    private final Set<Class<?>> excluded = new LinkedHashSet<>();
    private @Nullable CheckMode mode;
    private @Nullable String name;

    Builder(Muster muster, Class<I> inputType) {
      this.muster = muster;
      this.inputType = inputType;
    }

    /**
     * Lists rules to check, in the order they should run. Each must be registered with the {@code
     * Muster} and accept inputs of the binding's type.
     *
     * @param ruleClasses the rules
     * @return this builder
     * @throws IllegalArgumentException if a rule is listed twice
     */
    @SafeVarargs
    public final Builder<I> rules(Class<? extends BusinessRule<?>>... ruleClasses) {
      for (var ruleClass : ruleClasses) {
        Muster.requireDistinct(listed, ruleClass, "listed");
      }
      return this;
    }

    /**
     * Leaves out rules that would bind by type. A listed rule cannot require an excluded rule; a
     * rule bound by type that requires one is left out too.
     *
     * @param ruleClasses the rules, registered with the {@code Muster}
     * @return this builder
     * @throws IllegalArgumentException if a rule is excluded twice
     */
    @SafeVarargs
    public final Builder<I> exclude(Class<? extends BusinessRule<?>>... ruleClasses) {
      for (var ruleClass : ruleClasses) {
        Muster.requireDistinct(excluded, ruleClass, "excluded");
      }
      return this;
    }

    /**
     * Sets the mode the binding checks in. Unless set, it is the mode of the {@code Muster}.
     *
     * @param mode the mode
     * @return this builder
     */
    public Builder<I> mode(CheckMode mode) {
      this.mode = requireNonNull(mode, "mode");
      return this;
    }

    /**
     * Names the binding in logs, for example after the method it guards. Unless set, the name is
     * the input type.
     *
     * @param name the name
     * @return this builder
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public Builder<I> name(String name) {
      requireNonNull(name, "name");
      if (name.isBlank()) {
        throw new IllegalArgumentException("name must not be blank");
      }
      this.name = name;
      return this;
    }

    /**
     * Builds the binding. For a final input type, such as a record, it also binds the rules now and
     * logs them; for other types, each class is bound on its first check.
     *
     * @return the binding
     * @throws IllegalArgumentException if a listed rule is not registered or does not accept the
     *     binding's type, an excluded rule is not registered or is also listed, or a listed rule
     *     requires an excluded rule, directly or through other rules
     */
    public Binding<I> build() {
      var catalog = muster.catalog();
      var wrapped = Muster.wrap(inputType);
      requireSelectable(
          catalog,
          listed,
          excluded,
          ruleClass -> catalog.requireListable(ruleClass, inputType, wrapped));
      return new Binding<>(
          muster,
          wrapped,
          new Selection(
              List.copyOf(listed),
              Set.copyOf(excluded),
              name == null ? wrapped.getTypeName() : name),
          mode == null ? muster.mode() : mode);
    }
  }

  /**
   * Checks the listed and excluded rules of a binding: each listed rule passes the given check,
   * each excluded one is registered and not listed, and no enabled listed rule requires an excluded
   * one.
   */
  static void requireSelectable(
      RuleCatalog catalog,
      Set<Class<?>> listed,
      Set<Class<?>> excluded,
      Consumer<Class<?>> requireListable) {
    listed.forEach(requireListable);
    for (var ruleClass : excluded) {
      catalog.registered(ruleClass);
      if (listed.contains(ruleClass)) {
        throw new IllegalArgumentException(
            ruleClass.getTypeName() + " is both listed and excluded");
      }
    }
    var problems = new ArrayList<String>();
    for (var ruleClass : listed) {
      if (catalog.isDisabled(ruleClass)) {
        continue;
      }
      var required = catalog.excludedRequirement(ruleClass, excluded);
      if (required != null) {
        problems.add(
            "'"
                + catalog.registered(ruleClass).id()
                + "' requires '"
                + catalog.registered(required).id()
                + "'");
      }
    }
    if (!problems.isEmpty()) {
      throw new IllegalArgumentException(
          "listed rules require excluded rules, directly or through other rules: "
              + String.join("; ", problems)
              + "; list other rules, or stop excluding what they require");
    }
  }
}
