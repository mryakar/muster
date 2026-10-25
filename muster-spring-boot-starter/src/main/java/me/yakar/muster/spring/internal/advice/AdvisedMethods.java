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
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.stream.Collectors;
import me.yakar.muster.rule.RuleTarget;
import me.yakar.muster.spring.Rules;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Service;

/**
 * Tells which methods Muster advises: those with {@link Rules}, and the public methods of a
 * {@code @Service} bean that take a {@link RuleTarget}, whose rules bind to it by type.
 *
 * <p>A call may reach a method through an interface, a bridge method or a method of a base class.
 * As for {@code @Transactional}, what counts is the method of the target class that the call runs.
 */
final class AdvisedMethods {

  private AdvisedMethods() {}

  /**
   * Returns the method of the target class that a call of the given method runs, its bridge
   * resolved, if Muster advises it, or {@code null}. A target that is a proxy based on interfaces
   * itself, such as a Spring Data repository, has only the methods of its interfaces.
   */
  static @Nullable Method advised(Method method, Class<?> targetClass) {
    var specific =
        Proxy.isProxyClass(targetClass)
            ? method
            : AopUtils.getMostSpecificMethod(method, targetClass);
    if (specific.getDeclaringClass() == Object.class) {
      return null;
    }
    if (rules(specific) != null) {
      return specific;
    }
    var modifiers = specific.getModifiers();
    var service =
        Modifier.isPublic(modifiers)
            && !Modifier.isStatic(modifiers)
            // The class itself, or one of its annotations, is a service; its base class is not
            // enough.
            && AnnotatedElementUtils.isAnnotated(targetClass, Service.class)
            && Arrays.stream(specific.getParameterTypes())
                .anyMatch(RuleTarget.class::isAssignableFrom);
    return service ? specific : null;
  }

  /**
   * Fails unless a proxy can check the rules of an advised method before it runs.
   *
   * @throws IllegalStateException if the method is not public, is static or final, does not take
   *     exactly one parameter, or names more than one mode
   */
  static void requireCheckable(Method method, @Nullable Rules rules, String label) {
    var modifiers = method.getModifiers();
    if (!Modifier.isPublic(modifiers)) {
      throw new IllegalStateException(
          label + " has rules but is not public; a proxy calls public methods only");
    }
    if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
      throw new IllegalStateException(
          label
              + " has rules but is "
              + (Modifier.isStatic(modifiers) ? "static" : "final")
              + ", so a proxy cannot check them before it runs");
    }
    var count = method.getParameterCount();
    if (count != 1) {
      throw new IllegalStateException(
          label
              + (count == 0
                  ? " has rules but no parameter; rules check the only parameter of a method"
                  : " takes "
                      + count
                      + " parameters; a method with rules takes one, so pass a command object"
                      + " that holds them, such as a record"));
    }
    if (rules != null && rules.mode().length > 1) {
      throw new IllegalStateException(
          "@Rules on "
              + label
              + " names "
              + rules.mode().length
              + " modes; give one, or none for the mode of Muster");
    }
  }

  /** Finds {@link Rules} as Spring finds {@code @Transactional}. */
  static @Nullable Rules rules(Method method) {
    return AnnotatedElementUtils.findMergedAnnotation(method, Rules.class);
  }

  /**
   * Names a method for logs and errors, such as {@code VehicleService.save(CreateVehicle)}: after
   * the target class, or after the interface for a proxy based on interfaces.
   */
  static String label(Method method, Class<?> targetClass) {
    var named = Proxy.isProxyClass(targetClass) ? method.getDeclaringClass() : targetClass;
    return named.getSimpleName()
        + "."
        + method.getName()
        + Arrays.stream(method.getParameterTypes())
            .map(Class::getSimpleName)
            .collect(Collectors.joining(", ", "(", ")"));
  }
}
