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
package me.yakar.muster.spring.internal.advice;

import me.yakar.muster.Checked;
import org.jspecify.annotations.Nullable;

/**
 * The check of the advised method that runs on each thread, which the {@code CheckedValues} bean
 * reads.
 *
 * <p>Concurrency: one thread-local value, which only its own thread sets and reads. The interceptor
 * marks the thread as checking before the rules of a method run, binds the check once it passes,
 * and restores what it replaced when the method returns or throws, so that a nested advised call
 * leaves the values of the outer one as they were, and its rules never read them. A thread that
 * runs no advised method holds no value, so that a pooled thread keeps none after a call. A {@link
 * Checked} is immutable.
 */
public final class CurrentCheck {

  /** What a thread holds while the rules of an advised method are checked. */
  private static final Object CHECKING = new Object();

  /** The check of the running advised method, {@link #CHECKING}, or nothing. */
  private static final ThreadLocal<Object> CURRENT = new ThreadLocal<>();

  private CurrentCheck() {}

  /**
   * Returns the check of the advised method that runs on this thread.
   *
   * @return the check, or {@code null} if no advised method runs on this thread
   * @throws IllegalStateException if the rules of an advised method are being checked on this
   *     thread
   */
  public static @Nullable Checked get() {
    var current = CURRENT.get();
    if (current == CHECKING) {
      throw new IllegalStateException(
          "the rules of a @Rules method are being checked on this thread; a rule reads the values"
              + " of the rules it requires from its context, with ctx.valueOf");
    }
    return (Checked) current;
  }

  /**
   * Returns whether the rules of an advised method are being checked on this thread.
   *
   * @return whether they are
   */
  public static boolean checking() {
    return CURRENT.get() == CHECKING;
  }

  /**
   * Marks this thread as checking the rules of an advised method.
   *
   * @return what it held, to {@link #restore} when the method returns or throws
   */
  static @Nullable Object startCheck() {
    var previous = CURRENT.get();
    CURRENT.set(CHECKING);
    return previous;
  }

  /** Binds the check of the advised method, which passed, to this thread. */
  static void bind(Checked checked) {
    CURRENT.set(checked);
  }

  /** Restores what {@link #startCheck} replaced, or nothing if it replaced nothing. */
  static void restore(@Nullable Object previous) {
    if (previous == null) {
      CURRENT.remove();
    } else {
      CURRENT.set(previous);
    }
  }
}
