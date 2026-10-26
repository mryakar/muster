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

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import me.yakar.muster.rule.RequiresRule;
import me.yakar.muster.rule.Rule;
import org.jspecify.annotations.Nullable;

/**
 * What Muster knows about a rule class before it has an instance.
 *
 * @param ruleClass the rule class
 * @param id the rule id
 * @param inputType the type of input the rule checks
 * @param requires the rules that must pass first, in the order the rule lists them
 * @param elementType the type of the elements of the collections the rule checks, or {@code null}
 *     if its input type is not a collection
 */
public record RuleDefinition(
    Class<?> ruleClass,
    String id,
    Class<?> inputType,
    List<Class<?>> requires,
    @Nullable Class<?> elementType) {

  /**
   * Reads the definition of a rule class.
   *
   * @param ruleClass the rule class
   * @return the definition
   * @throws IllegalArgumentException if the class has no {@code @Rule}, an invalid id, or no input
   *     type, requires a rule twice, or leaves out a {@code @RequiresRule} that a supertype
   *     declares
   */
  public static RuleDefinition of(Class<?> ruleClass) {
    return new RuleDefinition(
        ruleClass,
        idOf(ruleClass),
        RuleTypes.inputType(ruleClass),
        requires(ruleClass),
        RuleTypes.elementType(ruleClass));
  }

  /**
   * Returns whether the rule checks whole collections of the given class of elements. Muster passes
   * such a rule a copy of the collection as a list, so its input type must be one that a list is,
   * such as {@code List} or {@code Collection}, and its elements must accept the class.
   *
   * @param elementClass the class of the elements
   * @return whether the rule checks such collections
   */
  public boolean checksCollectionsOf(Class<?> elementClass) {
    return elementType != null
        && inputType.isAssignableFrom(List.class)
        && elementType.isAssignableFrom(elementClass);
  }

  /**
   * Reads the id of a rule class from its {@code @Rule}.
   *
   * @param ruleClass the rule class
   * @return the rule id
   * @throws IllegalArgumentException if the class has no {@code @Rule} or an invalid id
   */
  public static String idOf(Class<?> ruleClass) {
    var rule = ruleClass.getAnnotation(Rule.class);
    if (rule == null) {
      throw new IllegalArgumentException(
          ruleClass.getTypeName() + " is not annotated with @Rule" + hint(ruleClass));
    }
    if (!RuleIds.isValid(rule.id())) {
      throw new IllegalArgumentException(
          "the id '"
              + rule.id()
              + "' of "
              + ruleClass.getTypeName()
              + " is invalid; "
              + RuleIds.FORMAT);
    }
    return rule.id();
  }

  private static List<Class<?>> requires(Class<?> ruleClass) {
    var annotation = ruleClass.getAnnotation(RequiresRule.class);
    if (annotation == null) {
      var declaring = supertypeRequiringRules(ruleClass);
      if (declaring != null) {
        throw new IllegalArgumentException(
            ruleClass.getTypeName()
                + " does not declare @RequiresRule, but "
                + declaring.getTypeName()
                + " does; @RequiresRule is not inherited, so declare the rules "
                + ruleClass.getSimpleName()
                + " requires on it");
      }
      return List.of();
    }
    var requires = List.<Class<?>>of(annotation.value());
    var seen = new HashSet<Class<?>>();
    for (var required : requires) {
      if (!seen.add(required)) {
        throw new IllegalArgumentException(
            ruleClass.getTypeName() + " requires " + required.getTypeName() + " twice");
      }
    }
    return requires;
  }

  /**
   * Finds a superclass or interface of a rule class that declares {@code @RequiresRule}, nearest
   * superclasses first. A rule would silently lose those requirements, since annotations are not
   * inherited.
   */
  private static @Nullable Class<?> supertypeRequiringRules(Class<?> ruleClass) {
    var pending = new ArrayDeque<Class<?>>();
    var seen = new HashSet<Class<?>>();
    addSupertypes(ruleClass, pending);
    while (!pending.isEmpty()) {
      var type = pending.removeFirst();
      if (seen.add(type)) {
        if (type.isAnnotationPresent(RequiresRule.class)) {
          return type;
        }
        addSupertypes(type, pending);
      }
    }
    return null;
  }

  private static void addSupertypes(Class<?> type, ArrayDeque<Class<?>> pending) {
    var superclass = type.getSuperclass();
    if (superclass != null) {
      pending.addLast(superclass);
    }
    pending.addAll(List.of(type.getInterfaces()));
  }

  /** Explains why a class that looks like a rule has no {@code @Rule}. */
  private static String hint(Class<?> ruleClass) {
    if (ruleClass.isHidden() || ruleClass.isAnonymousClass()) {
      return "; a rule cannot be a lambda or an anonymous class, it is a named class with @Rule";
    }
    for (var type = ruleClass.getSuperclass(); type != null; type = type.getSuperclass()) {
      if (type.isAnnotationPresent(Rule.class)) {
        return "; @Rule is not inherited from " + type.getTypeName();
      }
    }
    return "";
  }
}
