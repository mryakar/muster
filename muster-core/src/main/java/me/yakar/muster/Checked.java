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

import java.util.List;
import me.yakar.muster.internal.rule.Step;
import me.yakar.muster.rule.ValueRule;
import org.jspecify.annotations.Nullable;

/**
 * A check that passed, with the values its {@link ValueRule value rules} left, so that the caller
 * uses what the rules loaded instead of loading it again.
 *
 * {@snippet :
 * Checked checked = vehicles.check(command);
 * Vehicle vehicle = checked.valueOf(VehicleExists.class); // no second read
 * }
 *
 * <p>In a check of a collection, a rule of each element leaves a value for each element: read it
 * from the element, as in {@code checked.element(i).valueOf(VehicleExists.class)}, where {@code i}
 * is the position of the element in the collection. A value that a rule of the whole collection
 * left is read from the check itself.
 *
 * <p>A check returns its values only when it passes: a check that fails throws, and its exception
 * carries no value. A value lives for one check; Muster keeps none from one call to the next. A
 * check whose rules leave no value returns a shared instance that holds none.
 *
 * <p>Instances are immutable, and so may be passed to other threads.
 */
public final class Checked {

  /** The size of a check of a single input, which has no elements. */
  private static final int SINGLE = -1;

  /** The position of no element: the check itself. */
  private static final int NO_ELEMENT = -1;

  /** A check whose rules leave no value, of an input or of a collection. */
  static final Checked NONE = new Checked(null, null, SINGLE, NO_ELEMENT);

  /** A check of an empty collection, which runs no rule. */
  static final Checked EMPTY = new Checked(null, null, 0, NO_ELEMENT);

  /** The steps of the chain that checked, or {@code null} if no rule ran or left a value. */
  private final @Nullable List<Step> steps;

  /**
   * The value each step left, by its index: in a check of a collection, the values of each element,
   * by position, for a rule of each element. Taken over from the check, which no longer writes it.
   */
  private final @Nullable Object @Nullable [] values;

  /** The number of elements of a checked collection, or {@link #SINGLE}. */
  private final int size;

  /** The position of the element this is about, or {@link #NO_ELEMENT}. */
  private final int position;

  private Checked(
      @Nullable List<Step> steps, @Nullable Object @Nullable [] values, int size, int position) {
    this.steps = steps;
    this.values = values;
    this.size = size;
    this.position = position;
  }

  /** Returns the values of a check of a single input, which a chain with a value rule kept. */
  static Checked ofInput(List<Step> steps, @Nullable Object[] values) {
    return new Checked(steps, values, SINGLE, NO_ELEMENT);
  }

  /** Returns the values of a check of a collection, which a chain with a value rule kept. */
  static Checked ofCollection(List<Step> steps, @Nullable Object[] values, int size) {
    return new Checked(steps, values, size, NO_ELEMENT);
  }

  /**
   * Returns the value that a {@link ValueRule} of the check left.
   *
   * {@snippet :
   * Vehicle vehicle = checked.valueOf(VehicleExists.class);
   * }
   *
   * @param rule the class of the value rule
   * @param <V> the type of value it leaves
   * @return the value
   * @throws IllegalStateException if the rule is not one of the check, which runs the rules it
   *     lists or binds by type and those they require, less the disabled and excluded ones; if it
   *     did not run, because it or a rule it requires does not apply to the input, or the check was
   *     of an empty collection, which {@link #hasValue} tells; or if its value is read from the
   *     wrong place: of each element from the check of a collection, or of the whole collection
   *     from an element
   */
  @SuppressWarnings("unchecked") // The check kept only values of the rule's value type.
  public <V> V valueOf(Class<? extends ValueRule<?, V>> rule) {
    var value = find(rule);
    if (value == null) {
      throw new IllegalStateException(
          "rule '"
              + requireNonNull(steps).get(indexOf(steps, rule)).rule().id()
              + "' left no value, since it did not run: it, or a rule it requires, does not apply"
              + " to the input; ask hasValue("
              + rule.getSimpleName()
              + ".class) first");
    }
    return (V) value;
  }

  /**
   * Returns whether a {@link ValueRule} of the check left a value: whether it ran, which a rule
   * that does not always apply to its input may not have, and no rule of a check of an empty
   * collection has.
   *
   * @param rule the class of the value rule
   * @return whether {@link #valueOf} returns its value
   * @throws IllegalStateException if the rule is not one of the check, or if its value would be
   *     read from the wrong place, as {@link #valueOf} describes; not for a check of an empty
   *     collection, which runs no rule
   */
  public boolean hasValue(Class<? extends ValueRule<?, ?>> rule) {
    requireNonNull(rule, "rule");
    // A check of an empty collection knows no rule, and none of them ran.
    return this != EMPTY && find(rule) != null;
  }

  /**
   * Returns the values that the rules of the check left for one element of the checked collection.
   *
   * @param index the position of the element in the collection
   * @return the values of that element
   * @throws IllegalStateException if the check was not of a collection, or this is already about an
   *     element
   * @throws IndexOutOfBoundsException if {@code index} is not the position of an element
   */
  public Checked element(int index) {
    if (position != NO_ELEMENT) {
      throw new IllegalStateException(
          "these are the values of the element at ["
              + position
              + "] already; get the values of an element from the check of the collection");
    }
    if (steps == null && size != 0) {
      // A check whose rules leave no value knows nothing of its elements; reading fails anyway.
      return this;
    }
    if (size == SINGLE) {
      throw new IllegalStateException(
          "the check was of a single input, which has no elements; read its values with valueOf");
    }
    return new Checked(steps, values, size, checkIndex(index, size));
  }

  /**
   * Returns the value that a rule left where this reads it, or {@code null} if the rule did not
   * run.
   */
  private @Nullable Object find(Class<?> rule) {
    requireNonNull(rule, "rule");
    var steps = this.steps;
    if (steps == null) {
      throw new IllegalStateException(
          size == 0
              ? "the check was of an empty collection, which runs no rule, so "
                  + rule.getTypeName()
                  + " left no value"
              : "no rule of the check leaves a value; "
                  + rule.getTypeName()
                  + " is not a value rule of it");
    }
    var index = indexOf(steps, rule);
    if (index < 0) {
      throw new IllegalStateException(
          rule.getTypeName()
              + " is not a rule of the check: the check runs the rules it lists or binds by type"
              + " and those they require, less the disabled and excluded ones");
    }
    var step = steps.get(index);
    if (step.rule().valueType() == null) {
      throw new IllegalStateException(
          "rule '" + step.rule().id() + "' of the check leaves no value; it is not a ValueRule");
    }
    var value = requireNonNull(values)[index];
    var perElement = size != SINGLE && !step.whole();
    if (perElement == (position == NO_ELEMENT)) {
      throw new IllegalStateException(
          perElement
              ? "rule '"
                  + step.rule().id()
                  + "' leaves a value for each element; read it from the element, as in"
                  + " checked.element(i).valueOf("
                  + rule.getSimpleName()
                  + ".class)"
              : "rule '"
                  + step.rule().id()
                  + "' checks the whole collection; read its value from the check of the"
                  + " collection, not from an element");
    }
    return perElement && value != null ? ((@Nullable Object[]) value)[position] : value;
  }

  /** Returns the index of a rule class among the steps, or -1 if none is of it. */
  private static int indexOf(List<Step> steps, Class<?> rule) {
    for (var i = 0; i < steps.size(); i++) {
      if (steps.get(i).rule().definition().ruleClass() == rule) {
        return i;
      }
    }
    return -1;
  }

  private static int checkIndex(int index, int size) {
    if (index < 0 || index >= size) {
      throw new IndexOutOfBoundsException(
          "the index " + index + " is not the position of one of the " + size + " elements");
    }
    return index;
  }

  @Override
  public String toString() {
    var steps = this.steps;
    var builder = new StringBuilder("Checked[");
    if (position != NO_ELEMENT) {
      builder.append("element=").append(position).append(", ");
    } else if (size != SINGLE) {
      builder.append("elements=").append(size).append(", ");
    }
    builder.append("valueRules=[");
    var first = true;
    if (steps != null) {
      for (var i = 0; i < steps.size(); i++) {
        if (steps.get(i).rule().valueType() != null) {
          builder.append(first ? "" : ", ").append(steps.get(i).rule().id());
          first = false;
        }
      }
    }
    return builder.append("]]").toString();
  }
}
