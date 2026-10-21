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
 * Declares that a rule only makes sense once other rules have passed, as a check of a task's state
 * needs the task to exist.
 *
 * {@snippet :
 * @Rule(id = "task.ready")
 * @RequiresRule(TaskExists.class)
 * final class TaskReady implements BusinessRule<Long> { ... }
 * }
 *
 * <p>A rule runs after the rules it requires, and only if all of them passed for the call. If one
 * of them failed or did not apply, the rule is skipped and reports nothing. A chain that does not
 * list a required rule includes it anyway.
 *
 * <p>A required rule receives the same input, so it must accept the input type of the rule that
 * requires it. Rules cannot require each other in a cycle.
 *
 * <p>Like {@link Rule}, the annotation is not inherited, from superclasses or interfaces. So that a
 * rule cannot lose its requirements unnoticed, Muster rejects a rule that does not declare {@code
 * RequiresRule} while one of its supertypes does: declare the requirements on every rule class.
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
