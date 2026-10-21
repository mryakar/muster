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
package me.yakar.muster.internal;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.yakar.muster.rule.BusinessRule;
import org.jspecify.annotations.Nullable;

/** Reads the input type of a rule from its class declaration. */
public final class RuleTypes {

  private RuleTypes() {}

  /**
   * Returns the type of input a rule class checks: the {@code T} of {@link BusinessRule}, resolved
   * through superclasses and interfaces. A parameterized type resolves to its raw class.
   *
   * @param ruleClass the rule class
   * @return the input type
   * @throws IllegalArgumentException if the class does not name its input type
   */
  public static Class<?> inputType(Class<?> ruleClass) {
    var type = typeArgument(ruleClass, Map.of());
    var inputType = type == null ? null : rawClass(type);
    if (inputType == null) {
      throw new IllegalArgumentException(
          "cannot tell what "
              + ruleClass.getName()
              + " checks; a rule names its input type, as in 'implements BusinessRule<Input>'");
    }
    return inputType;
  }

  /**
   * Finds the type argument of {@link BusinessRule} in {@code type} and its supertypes.
   *
   * @param bindings what the type variables used in {@code type} stand for
   */
  private static @Nullable Type typeArgument(Type type, Map<TypeVariable<?>, Type> bindings) {
    Class<?> raw;
    var ownBindings = new HashMap<TypeVariable<?>, Type>();
    if (type instanceof ParameterizedType parameterized) {
      raw = (Class<?>) parameterized.getRawType();
      var variables = raw.getTypeParameters();
      var arguments = parameterized.getActualTypeArguments();
      for (var i = 0; i < variables.length; i++) {
        ownBindings.put(variables[i], bindings.getOrDefault(arguments[i], arguments[i]));
      }
    } else {
      raw = (Class<?>) type;
    }
    if (raw == BusinessRule.class) {
      return ownBindings.get(raw.getTypeParameters()[0]);
    }
    for (var supertype : supertypes(raw)) {
      var found = typeArgument(supertype, ownBindings);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private static List<Type> supertypes(Class<?> type) {
    var supertypes = new ArrayList<Type>();
    var superclass = type.getGenericSuperclass();
    if (superclass != null) {
      supertypes.add(superclass);
    }
    supertypes.addAll(List.of(type.getGenericInterfaces()));
    return supertypes;
  }

  private static @Nullable Class<?> rawClass(Type type) {
    if (type instanceof Class<?> plain) {
      return plain;
    }
    if (type instanceof ParameterizedType parameterized) {
      return (Class<?>) parameterized.getRawType();
    }
    // A type variable that nothing binds, as in 'class AnyRule<T> implements BusinessRule<T>'.
    return null;
  }
}
