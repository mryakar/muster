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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import me.yakar.muster.internal.rule.ChainPlan;
import me.yakar.muster.internal.rule.ChainPlan.Selection;
import me.yakar.muster.rule.BusinessRule;
import me.yakar.muster.rule.RuleTarget;
import org.jspecify.annotations.Nullable;

/**
 * The rules a collection is checked against, in one call: rules that check each element and rules
 * that check the whole collection, such as a limit on its size. They are the rules this binding
 * lists and, for elements that are {@link RuleTarget}s, the rules that bind to their class by type,
 * less the excluded ones. Get one from {@link Muster#collectionBinding}.
 *
 * <p>A rule checks the whole collection if its input type is one that a list is, such as {@code
 * List<E>} or {@code Collection<E>}, and its elements accept those of the collection; Muster passes
 * it a copy of the collection as a list. Any other rule checks each element, as {@link
 * RuleChain#checkAll} describes. A rule of the whole collection checks it once every element passed
 * the rules it requires; its violations name no element.
 *
 * <p>The listed rules run first, in the order they are listed; the rules bound by type follow, of
 * both kinds together, each after the rules it requires, in the order of their ids. Collection
 * bindings are thread-safe.
 *
 * @param <E> the type of the elements
 */
public final class CollectionBinding<E> {

  private final Muster muster;
  private final Class<?> elementType;
  private final Selection selection;
  private final CheckMode mode;

  /** The chain of the only class an element of a final type can have, or {@code null}. */
  private final @Nullable RuleChain<Object> only;

  /** The chains of the classes of elements checked so far, for a type that is not final. */
  private final ClassChains chains = new ClassChains(this::bind);

  private CollectionBinding(
      Muster muster, Class<?> elementType, Selection selection, CheckMode mode) {
    this.muster = muster;
    this.elementType = elementType;
    this.selection = selection;
    this.mode = mode;
    if (Modifier.isFinal(elementType.getModifiers())) {
      var bound = bind(elementType);
      bound.plan().log();
      this.only = bound.chain();
    } else {
      // Each class is bound on its first check; planning the declared type now fails at once for
      // listed rules, and what they require, that cannot check its elements.
      ChainPlan.collection(muster.catalog(), elementType, selection);
      this.only = null;
    }
  }

  /**
   * Checks a collection against the rules of this binding, in its {@linkplain #mode() mode}. One
   * violation rejects the whole call; a violation about an element names its position in its
   * target, as in {@code [3].name}. An empty collection passes without running a rule.
   *
   * @param inputs the inputs; unless the element type of this binding is final, all of one class;
   *     rules see an unmodifiable copy, taken when the check starts
   * @throws BusinessRuleViolationException if a rule is violated
   * @throws NullPointerException if an element is {@code null}
   * @throws IllegalArgumentException if the elements are of different classes, or one is not of the
   *     binding's type, which only unchecked code can pass
   */
  public void check(Collection<? extends E> inputs) {
    var elements = RuleChain.elements(inputs, elementType, only == null, "binding");
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
   * Describes this binding for developers and logs: its name, element type, mode, and the ids of
   * its listed and excluded rules.
   *
   * @return the description
   */
  @Override
  public String toString() {
    var catalog = muster.catalog();
    return "CollectionBinding[name="
        + selection.name()
        + ", elements="
        + elementType.getTypeName()
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
    private final Set<Class<?>> listed = new LinkedHashSet<>();
    private final Set<Class<?>> excluded = new LinkedHashSet<>();
    private @Nullable CheckMode mode;
    private @Nullable String name;

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
    public final Builder<E> exclude(Class<? extends BusinessRule<?>>... ruleClasses) {
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
    public Builder<E> mode(CheckMode mode) {
      this.mode = requireNonNull(mode, "mode");
      return this;
    }

    /**
     * Names the binding in logs, for example after the method it guards. Unless set, the name is "a
     * collection of" the element type.
     *
     * @param name the name
     * @return this builder
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public Builder<E> name(String name) {
      requireNonNull(name, "name");
      if (name.isBlank()) {
        throw new IllegalArgumentException("name must not be blank");
      }
      this.name = name;
      return this;
    }

    /**
     * Builds the binding. For a final element type, such as a record, it also binds the rules now
     * and logs them; for other types, each class of elements is bound on its first check.
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
      Binding.requireSelectable(
          catalog,
          listed,
          excluded,
          ruleClass -> catalog.requireListableInCollection(ruleClass, elementType, wrapped));
      return new CollectionBinding<>(
          muster,
          wrapped,
          new Selection(
              List.copyOf(listed),
              Set.copyOf(excluded),
              name == null ? "a collection of " + wrapped.getTypeName() : name),
          mode == null ? muster.mode() : mode);
    }
  }
}
