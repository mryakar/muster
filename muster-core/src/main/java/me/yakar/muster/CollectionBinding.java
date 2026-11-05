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

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import me.yakar.muster.internal.rule.ChainPlan;
import me.yakar.muster.internal.rule.ChainPlan.Selection;
import me.yakar.muster.rule.BusinessRule;
import me.yakar.muster.rule.RuleTarget;

/**
 * The rules a collection is checked against, in one call: rules of each element and rules of the
 * whole collection, such as a limit on its size. They are the rules this binding lists and, for
 * elements that are {@link RuleTarget}s, the rules that bind to their class by type, less the
 * excluded ones. Get one from {@link Muster#collectionBinding}.
 *
 * <p>A rule checks the whole collection if its input is a {@code List}, a {@code
 * SequencedCollection} or a {@code Collection} of elements that accept those of the collection: it
 * gets a copy of the collection as a list, once every element passed the rules it requires, and its
 * violations name no element. Any other rule checks each element, as {@link RuleChain#checkAll}
 * describes.
 *
 * <p>The listed rules run first, in the order they are listed; the rules bound by type follow, each
 * after the rules it requires, in the order of their ids. Collection bindings are thread-safe.
 *
 * @param <E> the type of the elements
 */
public final class CollectionBinding<E> {

  private final Muster muster;
  private final Class<?> elementType;
  private final Selection selection;
  private final CheckMode mode;

  /**
   * The chains of the classes of the rule targets checked so far, and of the element type for other
   * elements, bound when the binding is built; for a final element type, its one chain.
   */
  private final ClassChains chains;

  private CollectionBinding(
      Muster muster, Class<?> elementType, Selection selection, CheckMode mode) {
    this.muster = muster;
    this.elementType = elementType;
    this.selection = selection;
    this.mode = mode;
    this.chains = ClassChains.forType(elementType, this::bind);
    if (!Modifier.isFinal(elementType.getModifiers())
        && RuleTarget.class.isAssignableFrom(elementType)) {
      // The class of the elements is bound on their first check; planning the declared type now
      // fails at once for listed rules, and what they require, that cannot check its elements.
      ChainPlan.collection(muster.catalog(), elementType, selection);
    }
  }

  /**
   * Checks a collection against the rules of this binding, in its {@linkplain #mode() mode}. Any
   * violation rejects the whole call, and the target of a violation about an element starts with
   * its position, as in {@code [3].name}. An empty collection passes without running a rule.
   *
   * @param inputs the collection: rule targets of one class, since rules bind to that class, or
   *     none a rule target; rules see an unmodifiable copy, taken when the check starts
   * @throws BusinessRuleViolationException if a rule is violated
   * @throws NullPointerException if {@code inputs} or an element is {@code null}
   * @throws IllegalArgumentException if the elements are rule targets of different classes, or rule
   *     targets and others, or one is not of the binding's type, which only unchecked code can pass
   */
  public void check(Collection<? extends E> inputs) {
    var elements = RuleChain.elements(inputs, elementType, true, "binding");
    if (!elements.isEmpty()) {
      chains.of(RuleChain.chainKey(elements.getFirst(), elementType)).runAll(elements, mode);
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
   * each list in the order the rules run: the chain of the element type, bound when the binding was
   * built, for a type that is final or not a rule target, and the chain of the class of the rule
   * targets of each collection checked so far. The map is a copy, taken now, that later checks do
   * not change.
   *
   * @return the ids by class, unmodifiable
   */
  public Map<Class<?>, List<String>> ruleIds() {
    return chains.ruleIds();
  }

  /**
   * Describes this binding for developers and logs: its name, element type, mode, and the ids of
   * its listed and excluded rules.
   *
   * @return the description
   */
  @Override
  public String toString() {
    return "CollectionBinding[name="
        + selection.name()
        + ", elements="
        + elementType.getTypeName()
        + ", mode="
        + mode
        + ", "
        + selection.describe(muster.catalog())
        + "]";
  }

  private ClassChains.Bound bind(Class<?> type) {
    return muster.bindCollection(type, selection, mode);
  }

  /**
   * Builds a {@link CollectionBinding}. Get one from {@link Muster#collectionBinding}. A builder is
   * not safe for use by multiple threads.
   *
   * @param <E> the type of the elements
   */
  public static final class Builder<E> {

    private final Muster muster;
    private final Class<E> elementType;
    private final SelectionBuilder selection = new SelectionBuilder();

    Builder(Muster muster, Class<E> elementType) {
      this.muster = muster;
      this.elementType = elementType;
    }

    /**
     * Lists rules to check, in the order they should run. Each must be registered with the {@code
     * Muster}, and either accept elements of the binding's type or check whole collections of them.
     *
     * @param ruleClasses the rules
     * @return this builder
     * @throws IllegalArgumentException if a rule is listed twice
     */
    @SafeVarargs
    public final Builder<E> rules(Class<? extends BusinessRule<?>>... ruleClasses) {
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
    public final Builder<E> exclude(Class<? extends BusinessRule<?>>... ruleClasses) {
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
    public Builder<E> mode(CheckMode mode) {
      selection.mode(mode);
      return this;
    }

    /**
     * Names the binding in logs, in its description and for listeners, for example after the method
     * it guards. Unless set, the name is the element type.
     *
     * @param name the name
     * @return this builder
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public Builder<E> name(String name) {
      selection.name(name);
      return this;
    }

    /**
     * Builds the binding. For an element type that is final, such as a record, or not a rule
     * target, it also binds the rules of that type now and logs them; the rules of each class of
     * rule targets are bound on its first check.
     *
     * @return the binding
     * @throws IllegalArgumentException if a listed rule is not registered or neither accepts the
     *     elements nor checks whole collections of them, an excluded rule is not registered or is
     *     also listed, a listed rule requires an excluded rule, directly or through other rules, or
     *     a listed rule requires one that can check neither
     */
    public CollectionBinding<E> build() {
      var catalog = muster.catalog();
      var wrapped = Muster.wrap(elementType);
      return new CollectionBinding<>(
          muster,
          wrapped,
          selection.build(
              catalog,
              wrapped,
              ruleClass -> catalog.requireListableInCollection(ruleClass, elementType, wrapped)),
          selection.mode(muster));
    }
  }
}
