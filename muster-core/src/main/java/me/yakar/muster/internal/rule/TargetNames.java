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

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;

/**
 * The names a chain or a binding gives the targets of its violations instead of those the rules
 * report, as in {@code name} to {@code vehicleName}. A name also renames the targets inside it:
 * {@code address} to {@code shippingAddress} renames {@code address.street} and {@code address[0]},
 * and the longest name that matches wins. Immutable, and so safe to share between threads.
 */
public final class TargetNames {

  /** No names: every target stays as the rule reports it. */
  public static final TargetNames NONE = new TargetNames(new LinkedHashMap<>());

  /** The names, by the target they rename, in the order they were given. Never changed. */
  private final SequencedMap<String, String> names;

  /** The targets the names rename, the longest first, so that the first that matches wins. */
  private final List<String> longestFirst;

  private TargetNames(SequencedMap<String, String> names) {
    this.names = names;
    longestFirst =
        names.keySet().stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
  }

  /**
   * Returns these names and one more.
   *
   * @param from the target a rule reports
   * @param to the target the violation names instead
   * @return the names with the new one
   * @throws IllegalArgumentException if a target is blank, or {@code from} is renamed already
   */
  public TargetNames with(String from, String to) {
    requireTarget(from, "from");
    requireTarget(to, "to");
    if (names.containsKey(from)) {
      throw new IllegalArgumentException(
          "target '" + from + "' is renamed to '" + names.get(from) + "' already");
    }
    var more = new LinkedHashMap<>(names);
    more.put(from, to);
    return new TargetNames(more);
  }

  private static void requireTarget(String target, String what) {
    requireNonNull(target, what);
    if (target.isBlank()) {
      throw new IllegalArgumentException(what + " must not be blank");
    }
  }

  /**
   * Returns whether there are no names.
   *
   * @return whether every target stays as it is
   */
  public boolean isEmpty() {
    return names.isEmpty();
  }

  /**
   * Returns the target a violation names for the one a rule reports: renamed by the longest name
   * that is the target or starts it, followed by {@code .} or {@code [}, or else the target as it
   * is.
   *
   * @param target the target the rule reports
   * @return the target of the violation
   */
  public String rename(String target) {
    for (var from : longestFirst) {
      if (starts(target, from)) {
        return names.get(from) + target.substring(from.length());
      }
    }
    return target;
  }

  /** Returns whether a target is the given one, or a target inside it. */
  private static boolean starts(String target, String from) {
    if (!target.startsWith(from)) {
      return false;
    }
    if (target.length() == from.length()) {
      return true;
    }
    var next = target.charAt(from.length());
    return next == '.' || next == '[';
  }

  @Override
  public String toString() {
    return names.toString();
  }
}
