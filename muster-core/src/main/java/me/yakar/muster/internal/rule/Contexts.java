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

import java.security.Principal;
import java.util.Locale;
import me.yakar.muster.rule.RuleContext;
import org.jspecify.annotations.Nullable;

/**
 * Creates the contexts through which rules read the values of a check. Only the engine creates
 * them, so the public API of {@link RuleContext} does not offer it: the class registers how, when
 * it is initialized, which happens before any rule is prepared.
 */
public final class Contexts {

  /** Creates a context that reads the values of a check. */
  @FunctionalInterface
  public interface Factory {

    /**
     * Creates the context of a rule for one call.
     *
     * @param prepared the context prepared with the rule
     * @param locale the locale of the call
     * @param caller the caller, or {@code null}
     * @param reads the values the rule may read
     * @param values the values of the check, by slot
     * @return the context
     */
    RuleContext create(
        RuleContext prepared,
        Locale locale,
        @Nullable Principal caller,
        ValueReads reads,
        @Nullable Object[] values);
  }

  /** Set once, while {@link RuleContext} is initialized. */
  private static volatile @Nullable Factory factory;

  private Contexts() {}

  /**
   * Registers how contexts that read values are created. Called once, by the static initializer of
   * {@link RuleContext}, which touches no other class whose initializer reads this one.
   *
   * @param created the factory
   * @throws IllegalStateException if a factory is registered already
   */
  public static void register(Factory created) {
    requireNonNull(created, "factory");
    if (factory != null) {
      throw new IllegalStateException("the factory of contexts is registered already");
    }
    factory = created;
  }

  /**
   * Creates the context of a rule that may read values, for one call. A prepared rule holds a
   * context, so {@link RuleContext} is initialized before this is called.
   *
   * @param prepared the context prepared with the rule
   * @param locale the locale of the call
   * @param caller the caller, or {@code null}
   * @param reads the values the rule may read
   * @param values the values of the check, by slot
   * @return the context
   */
  public static RuleContext create(
      RuleContext prepared,
      Locale locale,
      @Nullable Principal caller,
      ValueReads reads,
      @Nullable Object[] values) {
    return requireNonNull(factory, "factory").create(prepared, locale, caller, reads, values);
  }
}
