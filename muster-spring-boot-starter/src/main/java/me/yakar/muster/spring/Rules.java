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
 * <p>The input is the only parameter of the method; a method that needs more takes a command object
 * that holds them, such as a record. The rules are the ones listed here, in this order, and, for a
 * {@link me.yakar.muster.rule.RuleTarget}, the rules that bind to its class by type, less the
 * excluded ones. Methods of a {@code @Service} bean check the rules bound by type without this
 * annotation.
 *
 * <p>Muster finds the annotation as Spring finds {@code @Transactional}: on the method, on the
 * method it overrides or implements, and as a meta-annotation. A method with rules must be public,
 * and neither static nor final, so that a proxy can call it. Wrong use stops the application when
 * it starts.
 *
 * <pre>{@code
 * @Rules(value = VehicleExists.class, mode = CheckMode.COLLECT_ALL)
 * public void delete(Long id) { ... }
 * }</pre>
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
   * Returns how far a check goes once a rule is violated: one mode, or none for the mode of Muster.
   *
   * @return the mode, if any
   */
  CheckMode[] mode() default {};
}
