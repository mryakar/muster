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
import java.util.function.Consumer;
import me.yakar.muster.Muster;
import org.jspecify.annotations.Nullable;

/**
 * What Muster does before an advised method runs: check its input, a single one or a collection,
 * against its rules.
 */
record MethodAdvice(
    String label, Consumer<@Nullable Object> rules, @Nullable TransactionGuard transaction) {

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
    var parameterType = method.getParameterTypes()[0];
    if (!Collection.class.isAssignableFrom(parameterType)) {
      var builder = muster.binding((Class<Object>) parameterType).name(label);
      if (annotation != null) {
        builder.rules(annotation.value()).exclude(annotation.exclude());
        if (annotation.mode().length == 1) {
          builder.mode(annotation.mode()[0]);
        }
      }
      var binding = builder.build();
      return new MethodAdvice(label, binding::check, transaction);
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
    var binding = builder.build();
    return new MethodAdvice(label, input -> binding.check((Collection<Object>) input), transaction);
  }

  void check(@Nullable Object input) {
    if (transaction != null) {
      transaction.requireTransaction(label);
    }
    rules.accept(input);
  }
}
