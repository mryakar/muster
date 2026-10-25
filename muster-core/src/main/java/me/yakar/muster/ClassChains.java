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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * The chains of the classes checked so far, each bound on the first check of its class. Kept in a
 * map rather than in a {@link ClassValue}, which would keep the rules alive for as long as the
 * input class, after the {@code Muster} is gone.
 */
final class ClassChains {

  // A check reads with get, which takes no lock; computeIfAbsent locks the bucket of a class that
  // is not the first in it, even when the class is already bound.
  private final ConcurrentMap<Class<?>, RuleChain<Object>> chains = new ConcurrentHashMap<>();

  private final Function<Class<?>, RuleChain<Object>> binder;

  ClassChains(Function<Class<?>, RuleChain<Object>> binder) {
    this.binder = binder;
  }

  /** Returns the chain of a class, binding it on its first check. */
  RuleChain<Object> of(Class<?> type) {
    var chain = chains.get(type);
    return chain != null ? chain : chains.computeIfAbsent(type, binder);
  }
}
