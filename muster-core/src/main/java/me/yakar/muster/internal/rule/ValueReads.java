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

import java.util.List;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The values that one rule may read, and where a check keeps them: each in a slot of an array that
 * the check fills as rules pass. In a check of a collection, the slot of a rule of each element
 * holds an array with the value of each element, by its position in the collection. Immutable, and
 * shared across threads by the steps of a chain.
 */
public final class ValueReads {

  private final Class<?>[] rules;
  private final int[] slots;
  private final boolean[] perElement;
  private final boolean given;

  private ValueReads(Class<?>[] rules, int[] slots, boolean[] perElement, boolean given) {
    this.rules = rules;
    this.slots = slots;
    this.perElement = perElement;
    this.given = given;
  }

  /**
   * Creates the reads of a context that a test built with the values it gave, each in the slot of
   * its index.
   *
   * @param rules the value rules whose values were given
   * @param perElement whether each was given for each element
   * @return the reads
   */
  public static ValueReads given(List<Class<?>> rules, boolean[] perElement) {
    var slots = new int[rules.size()];
    for (var i = 0; i < slots.length; i++) {
      slots[i] = i;
    }
    return new ValueReads(rules.toArray(Class<?>[]::new), slots, perElement.clone(), true);
  }

  /**
   * Returns which of the readable rules a rule class is.
   *
   * @param ruleClass the rule class
   * @return its index, or {@code -1} if its value cannot be read
   */
  public int indexOf(Class<?> ruleClass) {
    requireNonNull(ruleClass, "rule");
    for (var i = 0; i < rules.length; i++) {
      if (rules[i] == ruleClass) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Returns how many rules' values may be read.
   *
   * @return the number of readable rules
   */
  public int size() {
    return rules.length;
  }

  /**
   * Returns the class of a readable rule.
   *
   * @param index its index
   * @return the rule class
   */
  public Class<?> rule(int index) {
    return rules[index];
  }

  /**
   * Returns the slot of a readable rule.
   *
   * @param index its index
   * @return the slot
   */
  public int slot(int index) {
    return slots[index];
  }

  /**
   * Returns whether a readable rule leaves a value for each element.
   *
   * @param index its index
   * @return whether the slot holds the value of each element
   */
  public boolean perElement(int index) {
    return perElement[index];
  }

  /**
   * Returns whether a test gave the values, rather than rules of a check.
   *
   * @return whether the values were given
   */
  public boolean given() {
    return given;
  }

  /**
   * Returns the values that a rule may read in a chain: those of the value rules it requires,
   * directly or through other rules.
   *
   * @param earlier the steps of the chain before the rule
   * @param requires the positions of the rules it requires
   * @param collection whether the reads are for a check of a collection, where a rule of each
   *     element leaves a value for each element
   * @return the reads, or {@code null} if the rule may read no value
   */
  static @Nullable ValueReads of(List<Step> earlier, int[] requires, boolean collection) {
    // What a required rule may read, it has read through its own requirements already.
    var reachable = new TreeSet<Integer>();
    for (var index : requires) {
      var required = earlier.get(index);
      if (required.rule().valueType() != null) {
        reachable.add(index);
      }
      var through = required.reads();
      if (through != null) {
        for (var slot : through.slots) {
          reachable.add(slot);
        }
      }
    }
    if (reachable.isEmpty()) {
      return null;
    }
    var rules = new Class<?>[reachable.size()];
    var slots = new int[rules.length];
    var perElement = new boolean[rules.length];
    var i = 0;
    for (var slot : reachable) {
      var step = earlier.get(slot);
      rules[i] = step.rule().definition().ruleClass();
      slots[i] = slot;
      perElement[i] = collection && !step.whole();
      i++;
    }
    return new ValueReads(rules, slots, perElement, false);
  }
}
