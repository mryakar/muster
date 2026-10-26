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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import me.yakar.muster.CheckMode;
import me.yakar.muster.rule.BusinessRule;

/**
 * Checks the input of a method against business rules before the method runs, inside its
 * transaction.
 *
 * <p>The input is the only parameter of the method; a method that needs more takes a command
 * object, such as a record. The rules are the ones listed, in this order, and, for a {@link
 * me.yakar.muster.rule.RuleTarget}, the rules that bind to its class by type, less the excluded
 * ones. Public methods of a {@code @Service} bean that take a rule target check those without this
 * annotation.
 *
 * <p>A parameter that is a collection, such as {@code List<CreateVehicle>}, is checked as a {@link
 * me.yakar.muster.CollectionBinding} checks it: rules of each element, whose violations name the
 * position of the element, and rules of the whole collection, such as {@code
 * BusinessRule<List<CreateVehicle>>}. Its element type must be known: a raw {@code List}, {@code
 * List<?>}, {@code List<? super E>} or an unbound {@code List<T>} stops the application. A
 * collection of rule targets counts as a rule target for a {@code @Service}.
 *
 * <p>The annotation is found as Spring finds {@code @Transactional}: on the method, on a method it
 * overrides or implements, and as a meta-annotation. The method must be public and neither static
 * nor final; wrong use stops the application. A call through {@code this} checks no rules. Other
 * advice runs as Spring orders it: {@code @Validated} may run after the rules, a {@code @Cacheable}
 * hit checks none, and an {@code @Async} method checks them on its own thread.
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Rules {

  /**
   * Returns the rules to check, in the order they should run.
   *
   * @return the rules
   */
  Class<? extends BusinessRule<?>>[] value() default {};

  /**
   * Returns the rules not to check, among those bound by type.
   *
   * @return the excluded rules
   */
  Class<? extends BusinessRule<?>>[] exclude() default {};

  /**
   * Returns the mode of the check: one mode, or none for the mode of the {@code Muster}.
   *
   * @return the mode, if any
   */
  CheckMode[] mode() default {};
}
