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
package me.yakar.muster.spring.internal.advice;

import static java.util.Objects.requireNonNull;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import me.yakar.muster.Binding;
import me.yakar.muster.CheckMode;
import me.yakar.muster.CollectionBinding;
import me.yakar.muster.Muster;
import me.yakar.muster.rule.BusinessRule;
import me.yakar.muster.rule.Rule;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * The advice of one method: the binding that checks its input, the rules the method lists and
 * excludes, and the transaction it must run in, if any.
 */
record MethodAdvice(
    String label,
    Class<?> targetClass,
    Checker checker,
    List<Class<? extends BusinessRule<?>>> listed,
    List<Class<? extends BusinessRule<?>>> excluded,
    @Nullable TransactionGuard transaction) {

  @SuppressWarnings("unchecked")
  private static final Class<? extends BusinessRule<?>>[] NO_RULES =
      (Class<? extends BusinessRule<?>>[]) new Class<?>[0];

  /**
   * Prepares the advice of the advised methods of a class, each mapped from every method that a
   * proxy may call for it: the method of the class, a bridge, or a method of an interface or a base
   * class.
   *
   * @throws IllegalStateException if an advised method cannot be checked, see {@link #of}
   */
  static Map<Method, MethodAdvice> ofClass(
      Muster muster, TransactionGuard.@Nullable Resolved transactions, Class<?> targetClass) {
    var candidates = new LinkedHashSet<Method>();
    if (!Proxy.isProxyClass(targetClass)) {
      candidates.addAll(List.of(ReflectionUtils.getAllDeclaredMethods(targetClass)));
    }
    for (var type : ClassUtils.getAllInterfacesForClassAsSet(targetClass)) {
      candidates.addAll(List.of(type.getMethods()));
    }
    var bySpecific = new HashMap<Method, MethodAdvice>();
    var advice = new HashMap<Method, MethodAdvice>();
    for (var method : candidates) {
      var specific = RulesPointcut.advised(method, targetClass);
      if (specific != null) {
        var methodAdvice = bySpecific.get(specific);
        if (methodAdvice == null) {
          methodAdvice = MethodAdvice.of(muster, transactions, specific, targetClass);
          bySpecific.put(specific, methodAdvice);
        }
        advice.put(method, methodAdvice);
      }
    }
    return Map.copyOf(advice);
  }

  /**
   * Prepares the advice of a method.
   *
   * @throws IllegalStateException if the method cannot be checked, see {@link
   *     RulesPointcut#requireCheckable}, or takes a collection whose element type is not known
   */
  @SuppressWarnings("unchecked")
  static MethodAdvice of(
      Muster muster,
      TransactionGuard.@Nullable Resolved transactions,
      Method method,
      Class<?> targetClass) {
    var label = RulesPointcut.label(method, targetClass);
    var annotation = RulesPointcut.rules(method);
    RulesPointcut.requireCheckable(method, annotation, label);
    var transaction = transactions == null ? null : transactions.forMethod(method, targetClass);
    var listed = annotation == null ? NO_RULES : annotation.value();
    var excluded = annotation == null ? NO_RULES : annotation.exclude();
    var mode = annotation == null || annotation.mode().length == 0 ? null : annotation.mode()[0];
    var parameterType = method.getParameterTypes()[0];
    Checker checker;
    if (Collection.class.isAssignableFrom(parameterType)) {
      var elementType = RulesPointcut.elementType(method, 0, targetClass);
      if (elementType == null) {
        throw new IllegalStateException(
            label
                + " takes a collection whose element type is not known, so Muster cannot tell"
                + " which rules check its elements; name it, as in List<CreateVehicle>");
      }
      var builder =
          muster
              .collectionBinding((Class<Object>) elementType)
              .name(label)
              .rules(listed)
              .exclude(excluded);
      if (mode != null) {
        builder.mode(mode);
      }
      checker = new Checker.OfElements(elementType, builder.build());
    } else {
      var builder =
          muster.binding((Class<Object>) parameterType).name(label).rules(listed).exclude(excluded);
      if (mode != null) {
        builder.mode(mode);
      }
      checker = new Checker.OfInput(parameterType, builder.build());
    }
    return new MethodAdvice(
        label, targetClass, checker, List.of(listed), List.of(excluded), transaction);
  }

  /** Checks the input of a call: that its transaction is active, if it needs one, and its rules. */
  void check(@Nullable Object input) {
    if (transaction != null) {
      transaction.requireTransaction(label);
    }
    checker.check(input);
  }

  /** Describes this advice for the actuator endpoint. */
  AdvisedMethod describe() {
    return new AdvisedMethod(
        label,
        targetClass,
        checker.inputType(),
        checker instanceof Checker.OfElements,
        checker.mode(),
        ids(listed),
        ids(excluded),
        checker.ruleIds());
  }

  /** The ids of rule classes, which Muster has checked are registered. */
  private static List<String> ids(List<Class<? extends BusinessRule<?>>> ruleClasses) {
    return ruleClasses.stream().map(ruleClass -> ruleClass.getAnnotation(Rule.class).id()).toList();
  }

  /**
   * What checks the input of an advised method: a binding of the input itself, or of the elements
   * of the collection it takes.
   */
  sealed interface Checker {

    /** Checks the input of a call. */
    void check(@Nullable Object input);

    /** Returns the type of the input, or of the elements of a collection. */
    Class<?> inputType();

    /** Returns the mode of the check. */
    CheckMode mode();

    /** Returns the ids of the rules of the chains bound so far, as the binding reports them. */
    Map<Class<?>, List<String>> ruleIds();

    /** A binding of the input itself. */
    record OfInput(Class<?> inputType, Binding<Object> binding) implements Checker {

      @Override
      public void check(@Nullable Object input) {
        binding.check(requireNonNull(input, "input"));
      }

      @Override
      public CheckMode mode() {
        return binding.mode();
      }

      @Override
      public Map<Class<?>, List<String>> ruleIds() {
        return binding.ruleIds();
      }
    }

    /** A binding of the elements of a collection, by their type. */
    record OfElements(Class<?> inputType, CollectionBinding<Object> binding) implements Checker {

      @Override
      @SuppressWarnings("unchecked")
      public void check(@Nullable Object input) {
        binding.check((Collection<Object>) requireNonNull(input, "inputs"));
      }

      @Override
      public CheckMode mode() {
        return binding.mode();
      }

      @Override
      public Map<Class<?>, List<String>> ruleIds() {
        return binding.ruleIds();
      }
    }
  }
}
