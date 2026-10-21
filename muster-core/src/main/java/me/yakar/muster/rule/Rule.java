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
package me.yakar.muster.rule;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a class as a {@link BusinessRule} and gives it its id.
 *
 * <p>The id identifies the rule wherever its class name could change: it is the default code of the
 * violations the rule reports, and the key that turns the rule on and off in configuration. Keep it
 * stable once the rule is in use.
 *
 * <p>An id starts with a lowercase letter and continues with lowercase letters, digits, dots and
 * hyphens. It does not end with a dot or a hyphen, and has no two of them in a row, as in {@code
 * vehicle.name-unique}.
 *
 * <p>The annotation is not inherited: a subclass of a rule is another rule and needs its own id.
 */
@Documented
@Retention(RUNTIME)
@Target(TYPE)
public @interface Rule {

  /**
   * Returns the id of the rule.
   *
   * @return the id, such as {@code vehicle.name-unique}
   */
  String id();
}
