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

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jspecify.annotations.Nullable;

/**
 * The advice of every prepared method, by the class of the target and then by the method a proxy
 * calls: the method of the class, a bridge, or a method of an interface or a base class. The {@link
 * RulesInterceptor} writes it, one class at a time, under its lock; a call reads it without one.
 */
public final class AdviceTable {

  private final ConcurrentMap<Class<?>, Map<Method, MethodAdvice>> prepared =
      new ConcurrentHashMap<>();

  AdviceTable() {}

  /** Returns the advice of the methods of a prepared class, or {@code null} for another class. */
  @Nullable Map<Method, MethodAdvice> get(Class<?> targetClass) {
    return prepared.get(targetClass);
  }

  /** Returns whether a class is prepared. */
  boolean contains(Class<?> targetClass) {
    return prepared.containsKey(targetClass);
  }

  /** Returns whether no class is prepared. */
  boolean isEmpty() {
    return prepared.isEmpty();
  }

  /** Adds the advice of the methods of a class, prepared. */
  void put(Class<?> targetClass, Map<Method, MethodAdvice> advice) {
    prepared.put(targetClass, advice);
  }
}
