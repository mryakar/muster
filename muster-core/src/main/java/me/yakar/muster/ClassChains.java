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

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import me.yakar.muster.internal.rule.ChainPlan;

/**
 * The chains of the classes checked so far, each bound on the first check of its class. Kept in a
 * map rather than in a {@link ClassValue}, which would keep the rules alive for as long as the
 * input class, after the {@code Muster} is gone.
 */
final class ClassChains {

  // A check reads with get, which takes no lock; computeIfAbsent locks the bucket of a class that
  // is not the first in it, even when the class is already bound.
  private final ConcurrentMap<Class<?>, RuleChain<Object>> chains = new ConcurrentHashMap<>();

  private final Function<Class<?>, Bound> binder;

  ClassChains(Function<Class<?>, Bound> binder) {
    this.binder = binder;
  }

  /** Returns the chain of a class, binding it on its first check. */
  RuleChain<Object> of(Class<?> type) {
    var chain = chains.get(type);
    return chain != null ? chain : bind(type);
  }

  /**
   * Binds a class, unless another thread has bound it meanwhile. Only the thread that binds it
   * logs, and only once the chain is in the map: computeIfAbsent holds the lock of the bucket while
   * it binds, and a logging backend runs code of the application.
   */
  private RuleChain<Object> bind(Class<?> type) {
    var bound = new ArrayList<Bound>(1);
    var chain =
        chains.computeIfAbsent(
            type,
            key -> {
              var made = binder.apply(key);
              bound.add(made);
              return made.chain();
            });
    bound.forEach(made -> made.plan().log());
    return chain;
  }

  /**
   * A chain bound to a class, and the plan it was made from.
   *
   * @param chain the chain
   * @param plan the plan, to log once the chain is in the map
   */
  record Bound(RuleChain<Object> chain, ChainPlan plan) {}
}
