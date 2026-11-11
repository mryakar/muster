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

import me.yakar.muster.Checked;
import me.yakar.muster.rule.ValueRule;
import me.yakar.muster.spring.internal.advice.CurrentCheck;

/**
 * The values that the {@linkplain ValueRule value rules} of a {@link Rules @Rules} method left when
 * its input was checked, for the method to use instead of loading the data again. Inject it and
 * read the values while the method runs:
 *
 * {@snippet :
 * @Service
 * class VehicleService {
 *
 *   private final CheckedValues values;
 *
 *   @Rules(VehicleOwnedByCaller.class)
 *   public Vehicle rename(RenameVehicle command) {
 *     Vehicle vehicle = values.valueOf(VehicleExists.class); // no second read
 *     return vehicle.renamed(command.name());
 *   }
 * }
 * }
 *
 * <p>The values are those of the {@code @Rules} method that runs on the calling thread, while it
 * runs: a method it calls reads them too, and an advised method it calls reads its own, until it
 * returns. They are not there during or before the check, after the method returns, or on any other
 * thread, such as in an {@code @Async} method that it calls; reading them there fails. A rule reads
 * the values of the rules it requires from its context, not from this bean.
 *
 * <p>The reads are those of {@link Checked}, which describes them. The starter registers the bean;
 * it is thread-safe.
 */
public final class CheckedValues {

  CheckedValues() {}

  /**
   * Returns the value that a value rule of the running {@code @Rules} method left.
   *
   * @param rule the class of the value rule
   * @param <V> the type of value it leaves
   * @return the value
   * @throws IllegalStateException if no {@code @Rules} method runs on this thread, its rules are
   *     being checked, or the check cannot read the value, as {@link Checked#valueOf} describes
   */
  public <V> V valueOf(Class<? extends ValueRule<?, V>> rule) {
    return current().valueOf(rule);
  }

  /**
   * Returns whether a value rule of the running {@code @Rules} method left a value: whether it ran,
   * which a rule that does not always apply to its input may not have.
   *
   * @param rule the class of the value rule
   * @return whether {@link #valueOf} returns its value
   * @throws IllegalStateException if no {@code @Rules} method runs on this thread, its rules are
   *     being checked, or as {@link Checked#hasValue} describes
   */
  public boolean hasValue(Class<? extends ValueRule<?, ?>> rule) {
    return current().hasValue(rule);
  }

  /**
   * Returns the values that the rules of the running {@code @Rules} method, which takes a
   * collection, left for one of its elements.
   *
   * @param index the position of the element in the collection
   * @return the values of that element
   * @throws IllegalStateException if no {@code @Rules} method runs on this thread, its rules are
   *     being checked, or as {@link Checked#element} describes
   * @throws IndexOutOfBoundsException if {@code index} is not the position of an element
   */
  public Checked element(int index) {
    return current().element(index);
  }

  private static Checked current() {
    var checked = CurrentCheck.get();
    if (checked == null) {
      throw new IllegalStateException(
          "no @Rules method runs on this thread; the values of its check are there while it runs,"
              + " on its own thread, and not before its check, after it returns, or on other"
              + " threads, such as in an @Async method that it calls");
    }
    return checked;
  }

  @Override
  public String toString() {
    if (CurrentCheck.checking()) {
      return "CheckedValues[the rules of a @Rules method are being checked]";
    }
    var checked = CurrentCheck.get();
    return "CheckedValues[" + (checked == null ? "no @Rules method runs" : checked) + "]";
  }
}
