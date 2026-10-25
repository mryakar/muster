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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import me.yakar.muster.internal.rule.ChainPlan;
import me.yakar.muster.internal.rule.ChainPlan.Selection;
import me.yakar.muster.internal.rule.PreparedRule;
import me.yakar.muster.internal.rule.RuleCatalog;
import me.yakar.muster.internal.rule.RuleDefinition;
import me.yakar.muster.internal.rule.RuleGraph;
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
 * <p>Muster checks the setup when it is built and when a chain or a binding is created, and rejects
 * a wrong one with an {@link IllegalArgumentException} or {@link IllegalStateException} that says
 * what is wrong. A chain then only runs its rules.
 */
public final class Muster {

  private static final System.Logger LOGGER = System.getLogger("me.yakar.muster");

  private final RuleCatalog catalog;
  private final Supplier<? extends @Nullable Principal> caller;
  private final Supplier<Locale> locale;
  private final CheckMode mode;

  /** The chains for rule targets, by the class of the input. */
  private final ClassChains targets = new ClassChains(this::bind);

  private Muster(
      RuleCatalog catalog,
      Supplier<? extends @Nullable Principal> caller,
      Supplier<Locale> locale,
      CheckMode mode) {
    this.catalog = catalog;
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
   * that are {@linkplain Builder#disable disabled}. Rules run in the order they are listed, after
   * the rules they require; see {@link RuleChain} for the order in full.
   *
   * @param inputType the type of input the chain checks; a primitive type stands for its wrapper
   * @param ruleClasses the rules, registered with this Muster, in the order they should run
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
    var wrapped = wrap(inputType);
    var listed = new LinkedHashSet<Class<?>>();
    for (var ruleClass : ruleClasses) {
      catalog.listed(ruleClass, inputType, wrapped);
      requireDistinct(listed, ruleClass, "listed");
    }
    var plan = ChainPlan.listed(catalog, wrapped.getTypeName(), listed);
    plan.log();
    return new RuleChain<>(wrapped, plan.steps(), caller, locale, mode);
  }

  /**
   * Starts a binding of inputs of the given type to the rules it lists and, for a rule target, to
   * the rules that bind to its class by type. Build it once, for example per service method, and
   * keep it.
   *
   * {@snippet :
   * Binding<VehicleFields> save = muster.binding(VehicleFields.class)
   *     .rules(VehicleNameUnique.class)
   *     .exclude(LegacyCodeFormat.class)
   *     .build();
   * save.check(command); // the listed rules, and the rules bound to the class of the command
   * }
   *
   * @param inputType the type of input the binding checks; a primitive type stands for its wrapper
   * @param <I> the type of input
   * @return a builder for the binding
   * @throws NullPointerException if {@code inputType} is {@code null}
   * @see Binding
   */
  public <I> Binding.Builder<I> binding(Class<I> inputType) {
    return new Binding.Builder<>(this, requireNonNull(inputType, "inputType"));
  }

  /**
   * Checks a rule target against every enabled rule whose input type accepts its class, in the mode
   * of this Muster; {@link #check(RuleTarget, CheckMode)} checks in another mode. A rule whose
   * input type is {@code Object} checks every target.
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
    targets.of(input.getClass()).run(input, mode);
  }

  /**
   * Checks a rule target as {@link #check(RuleTarget)} does, in the given mode instead of the mode
   * of this Muster. The rules of a class are bound once, whatever mode the checks use.
   *
   * @param input the input
   * @param mode the mode of this check
   * @throws BusinessRuleViolationException if a rule is violated
   * @throws NullPointerException if {@code input} or {@code mode} is {@code null}, or the locale
   *     supplier or a rule returns {@code null}
   * @throws IllegalStateException if a rule reports a violation under another rule's id
   */
  public void check(RuleTarget input, CheckMode mode) {
    requireNonNull(input, "input");
    requireNonNull(mode, "mode");
    targets.of(input.getClass()).run(input, mode);
  }

  private ClassChains.Bound bind(Class<?> type) {
    return bind(type, Selection.NONE, mode);
  }

  /**
   * Binds a class to the rules a selection lists and, for a rule target, to the rules that bind to
   * it by type. The caller logs the plan.
   */
  ClassChains.Bound bind(Class<?> type, Selection selection, CheckMode chainMode) {
    var plan = ChainPlan.bound(catalog, type, selection);
    return new ClassChains.Bound(
        new RuleChain<>(type, plan.steps(), caller, locale, chainMode), plan);
  }

  /** The rules registered with this Muster. */
  RuleCatalog catalog() {
    return catalog;
  }

  /** The mode of this Muster. */
  CheckMode mode() {
    return mode;
  }

  /** The JDK's own mapping from a primitive type to its wrapper; other types stay as they are. */
  static Class<?> wrap(Class<?> type) {
    return MethodType.methodType(type).wrap().returnType();
  }

  /** Adds a rule class to those seen so far, and rejects it if it is among them already. */
  static void requireDistinct(Set<Class<?>> seen, Class<?> ruleClass, String listing) {
    if (!seen.add(requireNonNull(ruleClass, "rule class"))) {
      throw new IllegalArgumentException(ruleClass.getTypeName() + " is " + listing + " twice");
    }
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
     * CheckMode#FAIL_FAST}. A chain can override it with {@link RuleChain#withMode}, and a single
     * check of a rule target with {@link Muster#check(RuleTarget, CheckMode)}.
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
      return new Muster(new RuleCatalog(rules, disabledClasses), caller, locale, mode);
    }

    /** A registered rule class and where its instance comes from. */
    private record Registration(RuleDefinition definition, Supplier<BusinessRule<?>> source) {}
  }
}
