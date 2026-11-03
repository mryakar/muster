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
package me.yakar.muster.spring.internal.selfinvocation;

import static java.lang.System.Logger.Level.WARNING;
import static java.util.Comparator.comparing;

import java.util.List;
import java.util.stream.Collectors;

/**
 * What the starter does when a method of an advised class calls an advised method on {@code this},
 * which passes no proxy and checks no rules; set with {@code muster.self-invocation}.
 */
public enum SelfInvocationMode {

  /**
   * Stops the application, or the call that prepares a class after it started, naming the methods.
   */
  FAIL,

  /** Logs the methods at WARNING and goes on. */
  WARN,

  /** Skips the scan. */
  OFF;

  private static final System.Logger LOGGER = System.getLogger("me.yakar.muster");

  /**
   * Fails on the calls found, naming them all in the order of their callers, in {@link #FAIL} mode.
   *
   * @param found the calls found
   * @throws IllegalStateException in {@link #FAIL} mode, if any call was found
   */
  public void requireNone(List<SelfInvocation> found) {
    if (this == FAIL && !found.isEmpty()) {
      throw new IllegalStateException(
          "Muster found calls of advised methods through this, which pass no proxy and check no"
              + " rules: "
              + sorted(found).stream().map(Object::toString).collect(Collectors.joining("; "))
              + ". Call the method through its bean: inject the bean itself, with @Lazy or an"
              + " ObjectProvider, or move the method to another bean; or set"
              + " muster.self-invocation=warn to log such calls instead");
    }
  }

  /**
   * Logs each call found at WARNING, in the order of their callers. In {@link #FAIL} mode, {@link
   * #requireNone} fails on them first.
   *
   * @param found the calls found
   */
  public void warn(List<SelfInvocation> found) {
    for (var call : sorted(found)) {
      LOGGER.log(
          WARNING,
          call + ", which passes no proxy and checks no rules; call the method through its bean");
    }
  }

  /**
   * Returns the mode the scan runs in: this one, or {@link #OFF} in a native image, which has no
   * class files to read. There, a mode other than {@link #OFF} logs a warning that the calls are
   * not looked for.
   *
   * @param nativeImage whether the application runs as a native image
   * @return the mode to scan in
   */
  public SelfInvocationMode inNativeImage(boolean nativeImage) {
    if (!nativeImage || this == OFF) {
      return this;
    }
    LOGGER.log(
        WARNING,
        "In a native image, Muster cannot read class files, so it does not look for methods that"
            + " call an advised method through this, which checks no rules; set"
            + " muster.self-invocation=off to leave this warning out");
    return OFF;
  }

  private static List<SelfInvocation> sorted(List<SelfInvocation> found) {
    return found.stream()
        .sorted(comparing(SelfInvocation::caller).thenComparing(SelfInvocation::callee))
        .toList();
  }
}
