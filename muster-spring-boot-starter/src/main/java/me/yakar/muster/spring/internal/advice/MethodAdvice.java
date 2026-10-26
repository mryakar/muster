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

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import me.yakar.muster.Binding;
import me.yakar.muster.CheckMode;
import me.yakar.muster.CollectionBinding;
import me.yakar.muster.Muster;
import me.yakar.muster.RuleChain;
import me.yakar.muster.rule.BusinessRule;
import me.yakar.muster.rule.Rule;
import org.jspecify.annotations.Nullable;

/**
 * The advice of one method: the binding that checks its input, the rules the method lists and
 * excludes, and the transaction it must run in, if any.
 */
record MethodAdvice(
    String label,
    Class<?> targetClass,
    Bound bound,
    List<Class<? extends BusinessRule<?>>> listed,
    List<Class<? extends BusinessRule<?>>> excluded,
    @Nullable TransactionGuard transaction) {

  /**
   * Prepares the advice of a method.
   *
   * @throws IllegalStateException if the method cannot be checked, see {@link
   *     AdvisedMethods#requireCheckable}, or takes a collection whose element type is not known
   */
  @SuppressWarnings("unchecked")
  static MethodAdvice of(
      Muster muster,
      TransactionGuard.@Nullable Resolved transactions,
      Method method,
      Class<?> targetClass) {
    var label = AdvisedMethods.label(method, targetClass);
    var annotation = AdvisedMethods.rules(method);
    AdvisedMethods.requireCheckable(method, annotation, label);
    var transaction = transactions == null ? null : transactions.forMethod(method, targetClass);
    var listed =
        annotation == null
            ? List.<Class<? extends BusinessRule<?>>>of()
            : List.of(annotation.value());
    var excluded =
        annotation == null
            ? List.<Class<? extends BusinessRule<?>>>of()
            : List.of(annotation.exclude());
    var parameterType = method.getParameterTypes()[0];
    if (!Collection.class.isAssignableFrom(parameterType)) {
      var builder = muster.binding((Class<Object>) parameterType).name(label);
      if (annotation != null) {
        builder.rules(annotation.value()).exclude(annotation.exclude());
        if (annotation.mode().length == 1) {
          builder.mode(annotation.mode()[0]);
        }
      }
      return new MethodAdvice(
          label,
          targetClass,
          new Bound.OfInput(parameterType, builder.build()),
          listed,
          excluded,
          transaction);
    }
    var elementType = AdvisedMethods.elementType(method, 0, targetClass);
    if (elementType == null) {
      throw new IllegalStateException(
          label
              + " takes a collection whose element type is not known, so Muster cannot tell"
              + " which rules check its elements; name it, as in List<CreateVehicle>");
    }
    var builder = muster.collectionBinding((Class<Object>) elementType).name(label);
    if (annotation != null) {
      builder.rules(annotation.value()).exclude(annotation.exclude());
      if (annotation.mode().length == 1) {
        builder.mode(annotation.mode()[0]);
      }
    }
    return new MethodAdvice(
        label,
        targetClass,
        new Bound.OfElements(elementType, builder.build()),
        listed,
        excluded,
        transaction);
  }

  void check(@Nullable Object input) {
    if (transaction != null) {
      transaction.requireTransaction(label);
    }
    bound.check(input);
  }

  /** Describes this advice for the actuator endpoint. */
  AdvisedMethod describe() {
    return new AdvisedMethod(
        label,
        targetClass,
        bound.inputType(),
        bound instanceof Bound.OfElements,
        bound.mode(),
        ids(listed),
        ids(excluded),
        bound.chains());
  }

  /** The ids of rule classes, which Muster has checked are registered. */
  private static List<String> ids(List<Class<? extends BusinessRule<?>>> ruleClasses) {
    return ruleClasses.stream().map(ruleClass -> ruleClass.getAnnotation(Rule.class).id()).toList();
  }

  /**
   * What checks the input of an advised method: a binding of the input itself, or of the elements
   * of the collection it takes.
   */
  sealed interface Bound {

    /** Checks the input of a call. */
    void check(@Nullable Object input);

    /** Returns the type of the input, or of the elements of a collection. */
    Class<?> inputType();

    /** Returns the mode of the check. */
    CheckMode mode();

    /** Returns the chains bound so far, as the binding reports them. */
    Map<Class<?>, RuleChain<?>> chains();

    /** A binding of the input itself. */
    record OfInput(Class<?> inputType, Binding<Object> binding) implements Bound {

      @Override
      public void check(@Nullable Object input) {
        binding.check(input);
      }

      @Override
      public CheckMode mode() {
        return binding.mode();
      }

      @Override
      public Map<Class<?>, RuleChain<?>> chains() {
        return binding.chains();
      }
    }

    /** A binding of the elements of a collection, by their type. */
    record OfElements(Class<?> inputType, CollectionBinding<Object> binding) implements Bound {

      @Override
      @SuppressWarnings("unchecked")
      public void check(@Nullable Object input) {
        binding.check((Collection<Object>) input);
      }

      @Override
      public CheckMode mode() {
        return binding.mode();
      }

      @Override
      public Map<Class<?>, RuleChain<?>> chains() {
        return binding.chains();
      }
    }
  }
}
