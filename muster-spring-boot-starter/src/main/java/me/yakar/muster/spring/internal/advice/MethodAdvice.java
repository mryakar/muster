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
import java.util.function.BiPredicate;
import me.yakar.muster.Binding;
import me.yakar.muster.Muster;
import org.jspecify.annotations.Nullable;

/** What Muster does before an advised method runs. */
record MethodAdvice(String label, Binding<Object> binding, @Nullable TransactionGuard transaction) {

  /**
   * Prepares the advice of a method.
   *
   * @throws IllegalStateException if the method cannot be checked, see {@link
   *     AdvisedMethods#requireCheckable}
   */
  @SuppressWarnings("unchecked")
  static MethodAdvice of(
      Muster muster,
      @Nullable TransactionGuard guard,
      @Nullable BiPredicate<Method, Class<?>> transactional,
      Method method,
      Class<?> targetClass) {
    var label = AdvisedMethods.label(method, targetClass);
    var rules = AdvisedMethods.rules(method);
    AdvisedMethods.requireCheckable(method, rules, label);
    var builder = muster.binding((Class<Object>) method.getParameterTypes()[0]).name(label);
    if (rules != null) {
      builder.rules(rules.value()).exclude(rules.exclude());
      if (rules.mode().length == 1) {
        builder.mode(rules.mode()[0]);
      }
    }
    var transaction =
        transactional != null && transactional.test(method, targetClass) ? guard : null;
    return new MethodAdvice(label, builder.build(), transaction);
  }

  void check(@Nullable Object input) {
    if (transaction != null) {
      transaction.requireTransaction(label);
    }
    binding.check(input);
  }
}
