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
package me.yakar.muster.spring;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Collectors;
import me.yakar.muster.rule.RuleTarget;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Service;

/**
 * Tells which methods Muster advises: those with {@link Rules}, and the public methods of a
 * {@code @Service} bean that take a {@link RuleTarget}, whose rules bind to it by type.
 */
final class AdvisedMethods {

  private AdvisedMethods() {}

  static boolean advised(Method method, Class<?> targetClass) {
    if (method.getDeclaringClass() == Object.class || method.isBridge() || method.isSynthetic()) {
      return false;
    }
    if (rules(method) != null) {
      return true;
    }
    var modifiers = method.getModifiers();
    return Modifier.isPublic(modifiers)
        && !Modifier.isStatic(modifiers)
        && AnnotatedElementUtils.hasAnnotation(targetClass, Service.class)
        && Arrays.stream(method.getParameterTypes()).anyMatch(RuleTarget.class::isAssignableFrom);
  }

  /** Finds {@link Rules} as Spring finds {@code @Transactional}. */
  static @Nullable Rules rules(Method method) {
    return AnnotatedElementUtils.findMergedAnnotation(method, Rules.class);
  }

  /** Names a method for logs and errors, such as {@code VehicleService.save(CreateVehicle)}. */
  static String label(Method method, Class<?> targetClass) {
    return targetClass.getSimpleName()
        + "."
        + method.getName()
        + Arrays.stream(method.getParameterTypes())
            .map(Class::getSimpleName)
            .collect(Collectors.joining(", ", "(", ")"));
  }
}
