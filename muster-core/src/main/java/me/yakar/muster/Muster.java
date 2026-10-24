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

import java.lang.System.Logger.Level;
import java.lang.invoke.MethodType;
import java.security.Principal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Supplier;
import me.yakar.muster.internal.PreparedRule;
import me.yakar.muster.internal.RuleDefinition;
import me.yakar.muster.internal.RuleGraph;
import me.yakar.muster.rule.BusinessRule;
import me.yakar.muster.rule.RuleTarget;
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

  private static final System.Logger LOGGER = System.getLogger("me.yakar.muster");

  private final Map<Class<?>, PreparedRule> rules;
  private final Set<Class<?>> disabled;
  private final Supplier<? extends @Nullable Principal> caller;
  private final Supplier<Locale> locale;
  private final CheckMode mode;

  /**
   * The chains for rule targets, by the class of the input. Kept here rather than in a {@link
   * ClassValue}, which would keep the rules alive for as long as the input class, after this Muster
   * is gone. A check reads it with {@code get}, which takes no lock; {@code computeIfAbsent} locks
   * the bucket of a class that is not the first in it, even when the class is already bound.
   */
  private final ConcurrentMap<Class<?>, RuleChain<Object>> bound = new ConcurrentHashMap<>();

  /** Binds a class to its rules. A field, so that a check does not create it on every call. */
  private final Function<Class<?>, RuleChain<Object>> binder = this::bind;

  private Muster(
      Map<Class<?>, PreparedRule> rules,
      Set<Class<?>> disabled,
      Supplier<? extends @Nullable Principal> caller,
      Supplier<Locale> locale,
      CheckMode mode) {
    this.rules = Map.copyOf(rules);
    this.disabled = Set.copyOf(disabled);
    this.caller = caller;
    this.locale = locale;
    this.mode = mode;
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
   * <p>The chain also includes the rules that the given ones {@linkplain
   * me.yakar.muster.rule.RequiresRule require}, and logs each one it adds. It leaves out the rules
   * that are {@linkplain Builder#disable disabled}.
   *
   * @param inputType the type of input the chain checks; a primitive type stands for its wrapper
   * @param ruleClasses the rules, registered with this Muster
   * @param <I> the type of input
   * @return the chain
   * @throws IllegalArgumentException if a rule is not registered or listed twice, or does not check
   *     inputs of {@code inputType}
   * @throws NullPointerException if {@code inputType} or a rule class is {@code null}
   */
  @SafeVarargs
  public final <I> RuleChain<I> chain(
      Class<I> inputType, Class<? extends BusinessRule<?>>... ruleClasses) {
    requireNonNull(inputType, "inputType");
    // The JDK's own mapping from a primitive type to its wrapper; other types stay as they are.
    var wrapped = MethodType.methodType(inputType).wrap().returnType();
    var included = new LinkedHashMap<Class<?>, PreparedRule>();
    var listed = new HashSet<Class<?>>();
    for (var ruleClass : ruleClasses) {
      requireNonNull(ruleClass, "rule class");
      var rule = rules.get(ruleClass);
      if (rule == null) {
        throw new IllegalArgumentException(ruleClass.getTypeName() + " is not registered");
      }
      if (!listed.add(ruleClass)) {
        throw new IllegalArgumentException(ruleClass.getTypeName() + " is listed twice");
      }
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
      if (!disabled.contains(ruleClass)) {
        included.put(ruleClass, rule);
      }
    }
    return compose(wrapped, included);
  }

  /**
   * Checks a rule target against every enabled rule whose input type accepts its class, in the mode
   * of this Muster. A rule whose input type is {@code Object} checks every target.
   *
   * <p>The rules bind to the class of the input at run time: an input declared as an interface
   * binds to the rules of its actual class. Muster prepares the chain for a class once, on its
   * first check, and keeps it for as long as the Muster is used. It logs the bound rules at {@code
   * DEBUG}, or a warning if no rule binds, since that usually means a rule was not registered.
   * Threads may check at the same time; the check of a class that is already bound takes no lock.
   *
   * @param input the input
   * @throws BusinessRuleViolationException if a rule is violated
   * @throws NullPointerException if {@code input} is {@code null}, or the locale supplier or a rule
   *     returns {@code null}
   * @throws IllegalStateException if a rule reports a violation under another rule's id
   * @see RuleChain#check
   */
  public void check(RuleTarget input) {
    requireNonNull(input, "input");
    var type = input.getClass();
    var chain = bound.get(type);
    (chain != null ? chain : bound.computeIfAbsent(type, binder)).check(input);
  }

  private RuleChain<Object> bind(Class<?> type) {
    var included = new LinkedHashMap<Class<?>, PreparedRule>();
    rules.forEach(
        (ruleClass, rule) -> {
          if (!disabled.contains(ruleClass)
              && rule.definition().inputType().isAssignableFrom(type)) {
            included.put(ruleClass, rule);
          }
        });
    RuleChain<Object> chain = compose(type, included);
    if (included.isEmpty()) {
      LOGGER.log(
          Level.WARNING,
          "No rule binds to "
              + type.getTypeName()
              + "; check that its rules are registered, enabled and accept it");
    } else {
      LOGGER.log(
          Level.DEBUG, () -> "Rules bound to " + type.getTypeName() + ": " + chain.ruleIds());
    }
    return chain;
  }

  /** Adds the rules that the included ones require, and orders them. */
  private <I> RuleChain<I> compose(Class<?> inputType, Map<Class<?>, PreparedRule> included) {
    var pending = new ArrayDeque<>(included.values());
    while (!pending.isEmpty()) {
      var rule = pending.removeFirst();
      for (var requiredClass : rule.definition().requires()) {
        if (!included.containsKey(requiredClass)) {
          var required = rules.get(requiredClass);
          included.put(requiredClass, required);
          pending.addLast(required);
          LOGGER.log(
              Level.INFO,
              "The chain for "
                  + inputType.getTypeName()
                  + " includes rule '"
                  + required.id()
                  + "' because '"
                  + rule.id()
                  + "' requires it");
        }
      }
    }
    var ordered =
        RuleGraph.order(included.values().stream().map(PreparedRule::definition).toList());
    var positions = new HashMap<Class<?>, Integer>();
    var steps = new ArrayList<RuleChain.Step>();
    for (var definition : ordered) {
      var requires = definition.requires().stream().mapToInt(positions::get).toArray();
      positions.put(definition.ruleClass(), steps.size());
      steps.add(new RuleChain.Step(included.get(definition.ruleClass()), requires));
    }
    return new RuleChain<>(inputType, steps, caller, locale, mode);
  }

  /** Builds a {@link Muster}. Get one from {@link Muster#builder()}. */
  public static final class Builder {

    private final Map<Class<?>, Registration> registrations = new LinkedHashMap<>();
    private final Map<String, Class<?>> ids = new LinkedHashMap<>();
    private final Set<String> disabled = new LinkedHashSet<>();
    private Supplier<? extends @Nullable Principal> caller = () -> null;
    private Supplier<Locale> locale = Locale::getDefault;
    private CheckMode mode = CheckMode.FAIL_FAST;

    private Builder() {}

    /**
     * Registers rule instances.
     *
     * @param rules the rules
     * @return this builder
     * @throws IllegalArgumentException if a rule has no {@code @Rule}, an invalid id or no input
     *     type, requires a rule twice, leaves out a {@code @RequiresRule} that a supertype
     *     declares, or if its class or id is already registered
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
     *     type, requires a rule twice, leaves out a {@code @RequiresRule} that a supertype
     *     declares, or if it or its id is already registered
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
      if (registrations.containsKey(ruleClass)) {
        throw new IllegalArgumentException(ruleClass.getTypeName() + " is registered twice");
      }
      var definition = RuleDefinition.of(ruleClass);
      var other = ids.get(definition.id());
      if (other != null) {
        throw new IllegalArgumentException(
            other.getTypeName()
                + " and "
                + ruleClass.getTypeName()
                + " share the id '"
                + definition.id()
                + "'");
      }
      registrations.put(ruleClass, new Registration(definition, source));
      ids.put(definition.id(), ruleClass);
    }

    private static BusinessRule<?> create(
        RuleFactory factory, Class<? extends BusinessRule<?>> ruleClass) {
      @Nullable BusinessRule<?> rule;
      try {
        rule = factory.create(ruleClass);
      } catch (Exception e) { // Checked ones too: a factory in another JVM language may throw them.
        throw new IllegalStateException(
            "the rule factory could not create " + ruleClass.getTypeName(), e);
      }
      if (rule == null) {
        throw new IllegalStateException(
            "the rule factory returned null for " + ruleClass.getTypeName());
      }
      if (!ruleClass.isInstance(rule)) {
        throw new IllegalStateException(
            "the rule factory returned a "
                + rule.getClass().getTypeName()
                + " for "
                + ruleClass.getTypeName());
      }
      return rule;
    }

    /**
     * Switches rules off without changing code, for example from configuration. A disabled rule
     * runs in no chain, but it is still checked and created, so that switching it on again cannot
     * reveal a setup error. Muster logs every disabled rule when it is built.
     *
     * <p>A rule that requires a disabled rule, directly or through other rules, must be disabled as
     * well; {@link #build()} names every such rule at once.
     *
     * <p>Muster reads the ids when it is built and does not change them while it runs: switching a
     * rule on or off takes a new Muster, which for an application usually means a restart.
     *
     * @param ids the ids of the rules to switch off
     * @return this builder
     */
    public Builder disable(String... ids) {
      for (var id : ids) {
        disabled.add(requireNonNull(id, "id"));
      }
      return this;
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
     * Sets how far a check goes once a rule is violated. Unless set, it is {@link
     * CheckMode#FAIL_FAST}. A chain can override it with {@link RuleChain#withMode}.
     *
     * @param mode the mode
     * @return this builder
     */
    public Builder mode(CheckMode mode) {
      this.mode = requireNonNull(mode, "mode");
      return this;
    }

    /**
     * Builds Muster, creating the rules registered with a factory.
     *
     * @return the Muster
     * @throws IllegalArgumentException if a disabled id belongs to no rule, a rule requires a rule
     *     that is not registered, does not accept its input or is disabled while the rule is not,
     *     or rules require each other in a cycle
     * @throws IllegalStateException if the factory fails, returns {@code null} or returns an
     *     instance of another class
     */
    public Muster build() {
      var disabledClasses = new LinkedHashSet<Class<?>>();
      for (var id : disabled) {
        var ruleClass = ids.get(id);
        if (ruleClass == null) {
          throw new IllegalArgumentException("cannot disable '" + id + "': no rule has that id");
        }
        disabledClasses.add(ruleClass);
      }
      var definitions = new LinkedHashMap<Class<?>, RuleDefinition>();
      registrations.forEach(
          (ruleClass, registration) -> definitions.put(ruleClass, registration.definition()));
      RuleGraph.validate(definitions, disabledClasses);
      var rules = new LinkedHashMap<Class<?>, PreparedRule>();
      registrations.forEach(
          (ruleClass, registration) ->
              rules.put(
                  ruleClass,
                  PreparedRule.of(registration.definition(), registration.source().get())));
      for (var id : disabled) {
        LOGGER.log(Level.WARNING, "Rule '" + id + "' is disabled");
      }
      return new Muster(rules, disabledClasses, caller, locale, mode);
    }

    /** A registered rule class and where its instance comes from. */
    private record Registration(RuleDefinition definition, Supplier<BusinessRule<?>> source) {}
  }
}
