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
 * Declares the rules that must pass before a rule runs, as a check of a task's state needs the task
 * to exist.
 *
 * {@snippet :
 * @Rule(id = "task.ready")
 * @RequiresRule(TaskExists.class)
 * final class TaskReady implements BusinessRule<Long> { ... }
 * }
 *
 * <p>The rule runs after the rules it requires, and is skipped if any of them failed or did not
 * apply. A chain includes required rules it does not list. A required rule must accept the input
 * type of the rule that requires it, and rules cannot require each other in a cycle.
 *
 * <p>In a check of a collection, a rule of each element requires what it requires element by
 * element. A rule of the whole collection, such as one of {@code List<E>} input, that requires a
 * rule of each element, such as one of {@code Object} input, checks the collection only if every
 * element passed that rule.
 *
 * <p>Like {@link Rule}, the annotation is not inherited. Muster rejects a rule class that does not
 * declare it while a supertype does; a class that requires nothing declares
 * {@code @RequiresRule({})}.
 */
@Documented
@Retention(RUNTIME)
@Target(TYPE)
public @interface RequiresRule {

  /**
   * Returns the rules that must pass first.
   *
   * @return the required rule classes
   */
  Class<? extends BusinessRule<?>>[] value();
}
