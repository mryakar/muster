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

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import me.yakar.muster.internal.rule.ChainPlan;
import me.yakar.muster.rule.RuleTarget;
import org.jspecify.annotations.Nullable;

/**
 * The chains of the classes checked so far, each bound on the first check of its class. Kept in a
 * map rather than in a {@link ClassValue}, which would keep the rules alive for as long as the
 * input class, after the {@code Muster} is gone.
 */
final class ClassChains {

  // A check reads with get, which takes no lock.
  private final ConcurrentMap<Class<?>, RuleChain<Object>> chains = new ConcurrentHashMap<>();

  private final Function<Class<?>, Bound> binder;

  /** The chain of the one class a final type has, bound when this was created, or {@code null}. */
  private final @Nullable RuleChain<Object> only;

  /** Creates the chains of classes that are each bound on their first check. */
  ClassChains(Function<Class<?>, Bound> binder) {
    this(binder, null);
  }

  private ClassChains(Function<Class<?>, Bound> binder, @Nullable RuleChain<Object> only) {
    this.binder = binder;
    this.only = only;
  }

  /**
   * Creates the chains of the inputs of a type. A final type has one class, so its chain is bound
   * now, and its plan logged. For a type that is not a rule target, the chain of the type, which
   * checks every input that is not a rule target, is bound now too; the class of each rule target
   * is bound on its first check.
   */
  static ClassChains forType(Class<?> type, Function<Class<?>, Bound> binder) {
    if (!Modifier.isFinal(type.getModifiers())) {
      var chains = new ClassChains(binder);
      if (!RuleTarget.class.isAssignableFrom(type)) {
        chains.of(type);
      }
      return chains;
    }
    var bound = binder.apply(type);
    bound.plan().log();
    var chains = new ClassChains(binder, bound.chain());
    chains.chains.put(type, bound.chain());
    return chains;
  }

  /** Returns the chain of a class, binding it on its first check. */
  RuleChain<Object> of(Class<?> type) {
    var chain = only;
    if (chain != null) {
      return chain;
    }
    chain = chains.get(type);
    return chain != null ? chain : bind(type);
  }

  /**
   * Binds a class. Binding changes nothing until its plan is logged, so threads that check a new
   * class at once may each bind it: the first to put its chain in the map logs the plan, and every
   * thread uses that chain. No lock is held while a class is bound or logged, since a logging
   * backend runs code of the application.
   */
  private RuleChain<Object> bind(Class<?> type) {
    var made = binder.apply(type);
    var bound = chains.putIfAbsent(type, made.chain());
    if (bound != null) {
      return bound;
    }
    made.plan().log();
    return made.chain();
  }

  /**
   * Returns the ids of the rules of the chains bound so far, by class in the order of their names:
   * a copy, taken now, that a class bound later does not change.
   */
  Map<Class<?>, List<String>> ruleIds() {
    var sorted = new TreeMap<Class<?>, List<String>>(Comparator.comparing(Class::getTypeName));
    chains.forEach((type, chain) -> sorted.put(type, chain.ruleIds()));
    return Collections.unmodifiableMap(sorted);
  }

  /**
   * A chain bound to a class, and the plan it was made from.
   *
   * @param chain the chain
   * @param plan the plan, to log once the chain is in the map
   */
  record Bound(RuleChain<Object> chain, ChainPlan plan) {}
}
