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
package me.yakar.muster;

import static java.util.Objects.requireNonNull;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import me.yakar.muster.internal.rule.ChainPlan.Selection;
import me.yakar.muster.internal.rule.RuleCatalog;
import org.jspecify.annotations.Nullable;

/**
 * What the builders of {@link Binding} and {@link CollectionBinding} collect: the listed and the
 * excluded rules, the mode and the name. Not safe for use by multiple threads.
 */
final class SelectionBuilder {

  private final Set<Class<?>> listed = new LinkedHashSet<>();
  private final Set<Class<?>> excluded = new LinkedHashSet<>();
  private @Nullable CheckMode mode;
  private @Nullable String name;

  void list(Class<?> ruleClass) {
    Muster.requireDistinct(listed, ruleClass, "listed");
  }

  void exclude(Class<?> ruleClass) {
    Muster.requireDistinct(excluded, ruleClass, "excluded");
  }

  void mode(CheckMode mode) {
    this.mode = requireNonNull(mode, "mode");
  }

  void name(String name) {
    requireNonNull(name, "name");
    if (name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    this.name = name;
  }

  /** Returns the mode set, or else the mode of the {@code Muster}. */
  CheckMode mode(Muster muster) {
    return mode == null ? muster.mode() : mode;
  }

  /**
   * Checks the listed and excluded rules, each listed one with the given check, and returns them
   * with the name set, or else the name of the type.
   */
  Selection build(RuleCatalog catalog, Class<?> type, Consumer<Class<?>> requireListable) {
    catalog.requireSelectable(listed, excluded, requireListable);
    return new Selection(
        List.copyOf(listed), Set.copyOf(excluded), name == null ? type.getTypeName() : name);
  }
}
