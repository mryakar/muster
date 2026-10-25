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
package me.yakar.muster.spi;

import me.yakar.muster.rule.BusinessRule;

/**
 * Creates the rule instances Muster checks, such as beans from a dependency injection container.
 *
 * <p>A {@code Muster} calls the factory once per rule class when it is built, and keeps the
 * instance. The factory can be a lambda or a method reference, such as {@code
 * injector::getInstance} or {@code rules::get}.
 */
@FunctionalInterface
public interface RuleFactory {

  /**
   * Returns an instance of the given rule class.
   *
   * @param ruleClass the rule class
   * @return an instance of {@code ruleClass}; must not be {@code null}
   */
  BusinessRule<?> create(Class<? extends BusinessRule<?>> ruleClass);
}
