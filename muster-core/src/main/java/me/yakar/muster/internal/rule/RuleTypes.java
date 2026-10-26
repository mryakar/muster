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
package me.yakar.muster.internal.rule;

import static java.util.Objects.requireNonNull;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.yakar.muster.rule.BusinessRule;
import org.jspecify.annotations.Nullable;

/** Reads the input type of a rule from its class declaration. */
final class RuleTypes {

  private RuleTypes() {}

  /**
   * Returns the type of input a rule class checks: the {@code T} of {@link BusinessRule}, resolved
   * through superclasses and interfaces. A parameterized type resolves to its raw class, and an
   * array of one to an array of the raw class.
   *
   * @param ruleClass the rule class
   * @return the input type
   * @throws IllegalArgumentException if the class does not name its input type
   */
  static Class<?> inputType(Class<?> ruleClass) {
    var found = typeArgument(ruleClass, BusinessRule.class, Map.of());
    var inputType = found == null ? null : rawClass(found.type());
    if (inputType == null) {
      throw new IllegalArgumentException(
          "cannot tell what "
              + ruleClass.getTypeName()
              + " checks; a rule names its input type, as in 'implements BusinessRule<Input>'");
    }
    return inputType;
  }

  /**
   * Returns the type of the elements of the collections a rule class checks: the {@code E} of the
   * {@link Collection} that the rule's input type is, as in {@code BusinessRule<List<E>>}. A
   * wildcard or a type variable stands for its upper bound, so {@code List<?>}, a raw {@code List}
   * and an unbound {@code List<T>} have {@code Object} elements.
   *
   * @param ruleClass the rule class, whose input type is known
   * @return the element type, or {@code null} if the input type is not a collection
   */
  static @Nullable Class<?> elementType(Class<?> ruleClass) {
    var input = requireNonNull(typeArgument(ruleClass, BusinessRule.class, Map.of()));
    // The input type was read first, so it is known.
    if (!Collection.class.isAssignableFrom(requireNonNull(rawClass(input.type())))) {
      return null;
    }
    return boundClass(
        requireNonNull(typeArgument(input.type(), Collection.class, input.bindings())).type());
  }

  /** Returns the class a type stands for, taking a wildcard or a type variable at its bound. */
  private static Class<?> boundClass(Type type) {
    if (type instanceof Class<?> plain) {
      return plain;
    }
    if (type instanceof ParameterizedType parameterized) {
      return (Class<?>) parameterized.getRawType();
    }
    if (type instanceof WildcardType wildcard) {
      return boundClass(wildcard.getUpperBounds()[0]);
    }
    if (type instanceof TypeVariable<?> variable) {
      return boundClass(variable.getBounds()[0]);
    }
    // The only other kind of type that reflection creates.
    return boundClass(((GenericArrayType) type).getGenericComponentType()).arrayType();
  }

  /**
   * A type argument, and what the type variables in it stand for, such as the {@code E} of a base
   * class in {@code BusinessRule<List<E>>}.
   */
  private record Found(Type type, Map<TypeVariable<?>, Type> bindings) {}

  /**
   * Finds the first type argument of {@code target} in {@code type} and its supertypes. A raw
   * {@code target} gives its own type variable.
   *
   * @param bindings what the type variables used in {@code type} stand for
   */
  private static @Nullable Found typeArgument(
      Type type, Class<?> target, Map<TypeVariable<?>, Type> bindings) {
    Class<?> raw;
    // Keeps the bindings of the levels below as well: a type argument found higher up may hold a
    // variable of any of them, as BusinessRule<X> does with X = List<E>. A type variable belongs to
    // the class that declares it, so the entries of the levels never collide.
    var ownBindings = new HashMap<TypeVariable<?>, Type>(bindings);
    if (type instanceof ParameterizedType parameterized) {
      raw = (Class<?>) parameterized.getRawType();
      var variables = raw.getTypeParameters();
      var arguments = parameterized.getActualTypeArguments();
      for (var i = 0; i < variables.length; i++) {
        ownBindings.put(variables[i], resolve(arguments[i], bindings));
      }
    } else {
      raw = (Class<?>) type;
    }
    if (raw == target) {
      var variable = raw.getTypeParameters()[0];
      return new Found(ownBindings.getOrDefault(variable, variable), bindings);
    }
    for (var supertype : supertypes(raw)) {
      var found = typeArgument(supertype, target, ownBindings);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  /**
   * Replaces the type variables in {@code type} that {@code bindings} binds, so that an array of a
   * bound variable, such as {@code E[]}, becomes a class.
   */
  private static Type resolve(Type type, Map<TypeVariable<?>, Type> bindings) {
    if (type instanceof TypeVariable<?> variable) {
      return bindings.getOrDefault(variable, variable);
    }
    if (type instanceof GenericArrayType array) {
      var component = rawClass(resolve(array.getGenericComponentType(), bindings));
      return component == null ? array : component.arrayType();
    }
    return type;
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
    // A type variable that nothing binds, as in 'class AnyRule<T> implements BusinessRule<T>', or
    // an array of one.
    return null;
  }
}
