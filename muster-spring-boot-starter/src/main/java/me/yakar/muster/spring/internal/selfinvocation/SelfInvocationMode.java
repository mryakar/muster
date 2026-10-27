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
  OFF
}
