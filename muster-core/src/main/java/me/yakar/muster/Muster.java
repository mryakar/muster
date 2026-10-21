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

import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

import java.lang.invoke.MethodType;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import me.yakar.muster.internal.PreparedRule;
import me.yakar.muster.internal.RuleDefinition;
import me.yakar.muster.rule.BusinessRule;
import me.yakar.muster.spi.RuleFactory;
import org.jspecify.annotations.Nullable;

/**
 * Checks business rules. Build one per application and keep it.
 *
 * {@snippet :
 * Muster muster = Muster.builder()
 *     .rules(new VehicleNameUnique(vehicles), new VehicleCodeFormat())
 *     .caller(() -> currentUser())
 *     .build();
 *
 * RuleChain<UpdateVehicle> update =
 *     muster.chain(UpdateVehicle.class, VehicleNameUnique.class, VehicleCodeFormat.class);
 * update.check(command); // throws BusinessRuleViolationException if a rule is violated
 * }
 *
 * <p>Muster checks the setup when it is built and when a chain is created, and rejects a wrong one
 * with an {@link IllegalArgumentException} or {@link IllegalStateException} that says what is
 * wrong. A chain then only runs its rules.
 */
public final class Muster {

  private final Map<Class<?>, PreparedRule> rules;
  private final Supplier<? extends @Nullable Principal> caller;
  private final Supplier<Locale> locale;

  private Muster(
      Map<Class<?>, PreparedRule> rules,
      Supplier<? extends @Nullable Principal> caller,
      Supplier<Locale> locale) {
    this.rules = Map.copyOf(rules);
    this.caller = caller;
    this.locale = locale;
  }

  /**
   * Starts building a Muster.
   *
   * @return the builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates a chain that checks inputs of the given type against the given rules. Create it once,
   * for example per service method, and keep it.
   *
   * @param inputType the type of input the chain checks; a primitive type stands for its wrapper
   * @param ruleClasses the rules, registered with this Muster
   * @param <I> the type of input
   * @return the chain
   * @throws IllegalArgumentException if a rule is not registered or listed twice, or does not check
   *     inputs of {@code inputType}
   */
  @SafeVarargs
  public final <I> RuleChain<I> chain(
      Class<I> inputType, Class<? extends BusinessRule<?>>... ruleClasses) {
    var wrapped = MethodType.methodType(inputType).wrap().returnType();
    var chained = new ArrayList<PreparedRule>();
    var listed = new HashSet<Class<?>>();
    for (var ruleClass : ruleClasses) {
      var rule = rules.get(ruleClass);
      if (rule == null) {
        throw new IllegalArgumentException(ruleClass.getName() + " is not registered");
      }
      if (!listed.add(ruleClass)) {
        throw new IllegalArgumentException(ruleClass.getName() + " is listed twice");
      }
      var ruleInputType = rule.definition().inputType();
      if (!ruleInputType.isAssignableFrom(wrapped)) {
        throw new IllegalArgumentException(
            ruleClass.getName()
                + " checks "
                + ruleInputType.getName()
                + ", which "
                + inputType.getName()
                + " is not");
      }
      chained.add(rule);
    }
    chained.sort(comparing(PreparedRule::id));
    return new RuleChain<>(chained, caller, locale);
  }

  /** Builds a {@link Muster}. Get one from {@link Muster#builder()}. */
  public static final class Builder {

    private final Map<Class<?>, RuleDefinition> definitions = new LinkedHashMap<>();
    private final Map<Class<?>, Supplier<BusinessRule<?>>> sources = new LinkedHashMap<>();
    private final Map<String, Class<?>> ids = new LinkedHashMap<>();
    private Supplier<? extends @Nullable Principal> caller = () -> null;
    private Supplier<Locale> locale = Locale::getDefault;

    private Builder() {}

    /**
     * Registers rule instances.
     *
     * @param rules the rules
     * @return this builder
     * @throws IllegalArgumentException if a rule has no {@code @Rule}, an invalid id or no input
     *     type, or if its class or id is already registered
     */
    public Builder rules(BusinessRule<?>... rules) {
      for (var rule : rules) {
        requireNonNull(rule, "rule");
        register(rule.getClass(), () -> rule);
      }
      return this;
    }

    /**
     * Registers rule classes whose instances the factory creates. The factory is called once per
     * class, when Muster is built.
     *
     * @param factory the factory
     * @param ruleClasses the rule classes
     * @return this builder
     * @throws IllegalArgumentException if a class has no {@code @Rule}, an invalid id or no input
     *     type, or if it or its id is already registered
     */
    @SafeVarargs
    public final Builder rules(
        RuleFactory factory, Class<? extends BusinessRule<?>>... ruleClasses) {
      requireNonNull(factory, "factory");
      for (var ruleClass : ruleClasses) {
        requireNonNull(ruleClass, "rule class");
        register(ruleClass, () -> create(factory, ruleClass));
      }
      return this;
    }

    private void register(Class<?> ruleClass, Supplier<BusinessRule<?>> source) {
      if (definitions.containsKey(ruleClass)) {
        throw new IllegalArgumentException(ruleClass.getName() + " is registered twice");
      }
      var definition = RuleDefinition.of(ruleClass);
      var other = ids.get(definition.id());
      if (other != null) {
        throw new IllegalArgumentException(
            other.getName()
                + " and "
                + ruleClass.getName()
                + " share the id '"
                + definition.id()
                + "'");
      }
      definitions.put(ruleClass, definition);
      sources.put(ruleClass, source);
      ids.put(definition.id(), ruleClass);
    }

    private static BusinessRule<?> create(
        RuleFactory factory, Class<? extends BusinessRule<?>> ruleClass) {
      @Nullable BusinessRule<?> rule;
      try {
        rule = factory.create(ruleClass);
      } catch (RuntimeException e) {
        throw new IllegalStateException(
            "the rule factory could not create " + ruleClass.getName(), e);
      }
      if (rule == null) {
        throw new IllegalStateException(
            "the rule factory returned null for " + ruleClass.getName());
      }
      if (!ruleClass.isInstance(rule)) {
        throw new IllegalStateException(
            "the rule factory returned a "
                + rule.getClass().getName()
                + " for "
                + ruleClass.getName());
      }
      return rule;
    }

    /**
     * Sets who makes a call. Muster asks once at the start of every check; {@code null} stands for
     * an anonymous or system call. Unless set, no call has a caller.
     *
     * @param caller supplies the caller of the current call
     * @return this builder
     */
    public Builder caller(Supplier<? extends @Nullable Principal> caller) {
      this.caller = requireNonNull(caller, "caller");
      return this;
    }

    /**
     * Sets the locale of a call. Muster asks once at the start of every check. Unless set, it is
     * the default locale of the JVM.
     *
     * @param locale supplies the locale of the current call
     * @return this builder
     */
    public Builder locale(Supplier<Locale> locale) {
      this.locale = requireNonNull(locale, "locale");
      return this;
    }

    /**
     * Builds Muster, creating the rules registered with a factory.
     *
     * @return the Muster
     * @throws IllegalStateException if the factory fails, returns {@code null} or returns an
     *     instance of another class
     */
    public Muster build() {
      var rules = new LinkedHashMap<Class<?>, PreparedRule>();
      definitions.forEach(
          (ruleClass, definition) ->
              rules.put(ruleClass, PreparedRule.of(definition, sources.get(ruleClass).get())));
      return new Muster(rules, caller, locale);
    }
  }
}
