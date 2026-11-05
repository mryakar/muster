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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import me.yakar.muster.internal.rule.ChainPlan.Selection;
import me.yakar.muster.rule.BusinessRule;
import me.yakar.muster.rule.RuleTarget;

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

  /**
   * The chains of the classes of the rule targets checked so far, and of the input type for other
   * inputs, bound when the binding is built; for a final input type, its one chain.
   */
  private final ClassChains chains;

  private Binding(Muster muster, Class<?> inputType, Selection selection, CheckMode mode) {
    this.muster = muster;
    this.inputType = inputType;
    this.selection = selection;
    this.mode = mode;
    this.chains = ClassChains.forType(inputType, this::bind);
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
    RuleChain.requireInstance(input, inputType, "binding");
    chains.of(RuleChain.chainKey(input, inputType)).run(input, mode);
  }

  /**
   * Checks the elements of a collection against the rules of this binding, in its {@linkplain
   * #mode() mode}. Each rule checks the elements that passed the rules it requires, at once if it
   * overrides {@link me.yakar.muster.rule.BusinessRule#checkAll}. Any violation rejects the whole
   * call, and the target of a violation about an element starts with its position, as in {@code
   * [3].name}. An empty collection passes. The rules check each element; a {@link
   * CollectionBinding} also checks rules of the whole collection.
   *
   * @param inputs the collection: rule targets of one class, or none a rule target; rules see an
   *     unmodifiable copy, taken when the check starts
   * @throws BusinessRuleViolationException if a rule is violated
   * @throws NullPointerException if an element is {@code null}
   * @throws IllegalArgumentException if the elements are rule targets of different classes, or rule
   *     targets and others, or one is not of the binding's type, which only unchecked code can pass
   */
  public void checkAll(Collection<? extends I> inputs) {
    var elements = RuleChain.elements(inputs, inputType, true, "binding");
    if (!elements.isEmpty()) {
      chains.of(RuleChain.chainKey(elements.getFirst(), inputType)).runAll(elements, mode);
    }
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
   * Returns the ids of the rules of the chains bound so far, by class in the order of their names,
   * each list in the order the rules run: the chain of the input type, bound when the binding was
   * built, for a type that is final or not a rule target, and the chain of the class of each rule
   * target checked so far. The map is a copy, taken now, that later checks do not change. Unlike
   * {@link RuleChain#ruleIds()}, a list since a chain has one order, the ids come by class, since a
   * binding keeps a chain for each class it checks.
   *
   * @return the ids by class, unmodifiable
   */
  public Map<Class<?>, List<String>> ruleIds() {
    return chains.ruleIds();
  }

  /**
   * Describes this binding for developers and logs: its name, input type, mode, and the ids of its
   * listed and excluded rules.
   *
   * @return the description
   */
  @Override
  public String toString() {
    return "Binding[name="
        + selection.name()
        + ", input="
        + inputType.getTypeName()
        + ", mode="
        + mode
        + ", "
        + selection.describe(muster.catalog())
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
    private final SelectionBuilder selection = new SelectionBuilder();

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
        selection.list(ruleClass);
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
        selection.exclude(ruleClass);
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
      selection.mode(mode);
      return this;
    }

    /**
     * Names the binding in logs, in its description and for listeners, for example after the method
     * it guards. Unless set, the name is the input type.
     *
     * @param name the name
     * @return this builder
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public Builder<I> name(String name) {
      selection.name(name);
      return this;
    }

    /**
     * Builds the binding. For an input type that is final, such as a record, or not a rule target,
     * it also binds the rules of that type now and logs them; the rules of each class of rule
     * targets are bound on its first check.
     *
     * @return the binding
     * @throws IllegalArgumentException if a listed rule is not registered or does not accept the
     *     binding's type, an excluded rule is not registered or is also listed, a listed rule
     *     requires an excluded rule, directly or through other rules, or a rule requires a rule of
     *     each element of a collection
     */
    public Binding<I> build() {
      var catalog = muster.catalog();
      var wrapped = Muster.wrap(inputType);
      return new Binding<>(
          muster,
          wrapped,
          selection.build(
              catalog,
              wrapped,
              ruleClass -> catalog.requireListable(ruleClass, inputType, wrapped)),
          selection.mode(muster));
    }
  }
}
