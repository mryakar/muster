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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.yakar.muster.Muster;
import me.yakar.muster.spring.internal.selfinvocation.SelfInvocation;
import me.yakar.muster.spring.internal.selfinvocation.SelfInvocationMode;
import me.yakar.muster.spring.internal.selfinvocation.SelfInvocationScanner;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * Checks the input of an advised method against its rules, then calls the method.
 *
 * <p>Lifecycle: the pointcut notes each class it advises when Spring proxies a bean. Noted classes
 * are prepared once all singletons exist, so a rule may depend on an advised service; a class noted
 * later, such as a lazy bean's, when it is noted; a class called during startup, on that call. A
 * wrong binding fails when its class is prepared.
 *
 * <p>Concurrency: a pass gets the {@code Muster} before it takes {@link #lock}, so that no bean is
 * created under the lock. A class that the thread of a pass notes meanwhile, such as when getting
 * the {@code Muster} or preparing a class creates an advised bean, starts no pass of its own: the
 * running pass prepares it, since the pass repeats until no noted class is left unprepared. A call
 * reads the {@link AdviceTable table} without a lock: two map lookups.
 */
final class RulesInterceptor implements MethodInterceptor, SmartInitializingSingleton {

  private final ObjectProvider<Muster> muster;
  private final ObjectProvider<TransactionGuard> transactions;
  private final SelfInvocationMode selfInvocation;

  private final Object lock = new Object();

  /**
   * The classes the pointcut advises that no pass has gone over yet; a pass removes a class whether
   * it prepares it or fails on it. Guarded by {@link #lock}.
   */
  private final Set<Class<?>> noted = new LinkedHashSet<>();

  private final AdviceTable table;

  /** Guarded by {@link #lock}. */
  private boolean started;

  /** Set while this thread runs a pass, so that a class it notes meanwhile joins that pass. */
  private final ThreadLocal<Boolean> inPass = new ThreadLocal<>();

  /** Whether the order of the transaction advice has been checked. Guarded by {@link #lock}. */
  private boolean orderChecked;

  RulesInterceptor(
      AdviceTable table,
      ObjectProvider<Muster> muster,
      ObjectProvider<TransactionGuard> transactions,
      SelfInvocationMode selfInvocation) {
    this.table = table;
    this.muster = muster;
    this.transactions = transactions;
    this.selfInvocation = selfInvocation;
  }

  /** Notes a class the pointcut advises; once the application has started, prepares it at once. */
  void note(Class<?> targetClass) {
    // The pointcut notes a class again for every new proxy, such as of a prototype bean.
    if (table.contains(targetClass)) {
      return;
    }
    boolean prepare;
    synchronized (lock) {
      noted.add(targetClass);
      prepare = started;
    }
    // The pass of another thread may fail on the class and drop it before this thread's pass
    // runs, so this thread prepares it until it is prepared or a pass here fails on it. A class
    // that the thread of a running pass notes joins that pass.
    while (prepare && inPass.get() == null) {
      prepareNoted();
      synchronized (lock) {
        prepare = !table.contains(targetClass);
        if (prepare) {
          noted.add(targetClass);
        }
      }
    }
  }

  @Override
  public void afterSingletonsInstantiated() {
    synchronized (lock) {
      started = true;
    }
    prepareNoted();
  }

  /**
   * Prepares the noted classes that are not prepared yet, and scans each for calls of its advised
   * methods on {@code this}. Muster, the guard and its transaction test are resolved before the
   * lock is taken: creating them may wait for a bean that another thread creates, and that thread
   * may note a class meanwhile.
   */
  private void prepareNoted() {
    if (inPass.get() != null) {
      return;
    }
    @Nullable TransactionGuard guard;
    boolean checkOrder;
    inPass.set(Boolean.TRUE);
    try {
      var muster = this.muster.getObject();
      guard = transactions.getIfAvailable();
      var resolved = guard == null ? null : guard.resolve();
      var selfInvocations = new ArrayList<SelfInvocation>();
      synchronized (lock) {
        // Preparing a class may note another, so the pass goes over a copy until none is left.
        for (var unprepared = unprepared(); !unprepared.isEmpty(); unprepared = unprepared()) {
          for (var targetClass : unprepared) {
            Map<Method, MethodAdvice> advice;
            List<SelfInvocation> found;
            // A class that fails is noted again when a bean of it is created, and fails again; the
            // classes noted after it do not fail with it.
            try {
              advice = MethodAdvice.ofClass(muster, resolved, targetClass);
              found =
                  selfInvocation == SelfInvocationMode.OFF
                      ? List.of()
                      : SelfInvocationScanner.find(targetClass, labels(advice));
            } finally {
              noted.remove(targetClass);
            }
            selfInvocations.addAll(found);
            // A class whose calls fail the pass stays unprepared, so that creating a bean of it
            // later fails again.
            if (found.isEmpty() || selfInvocation != SelfInvocationMode.FAIL) {
              table.put(targetClass, advice);
            }
          }
          selfInvocation.requireNone(selfInvocations);
        }
        checkOrder = guard != null && !orderChecked && !table.isEmpty();
        orderChecked |= checkOrder;
      }
      // Logged without the lock, so that a slow log does not hold up the threads that need it.
      selfInvocation.warn(selfInvocations);
    } finally {
      inPass.remove();
    }
    if (checkOrder) {
      guard.checkOrder(RulesAdvisor.ORDER);
    }
  }

  /** The advised methods of a class, each to the label its advice names it by. */
  private static Map<Method, String> labels(Map<Method, MethodAdvice> advice) {
    var labels = new HashMap<Method, String>();
    advice.forEach((method, methodAdvice) -> labels.put(method, methodAdvice.label()));
    return labels;
  }

  /**
   * Returns the noted classes that are not prepared yet, and drops those that another pass prepared
   * since they were noted. Called under {@link #lock}.
   */
  private List<Class<?>> unprepared() {
    noted.removeIf(table::contains);
    return List.copyOf(noted);
  }

  @Override
  public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
    var target = invocation.getThis();
    if (target == null) {
      throw new IllegalStateException(
          "Muster cannot check the rules of "
              + invocation.getMethod().toGenericString()
              + " without a target");
    }
    var methods = table.get(target.getClass());
    if (methods == null) {
      methods = prepareOnCall(target.getClass(), invocation.getMethod());
    }
    var advice = methods.get(invocation.getMethod());
    if (advice == null) {
      throw new IllegalStateException(
          "Muster has not prepared the rules of "
              + RulesPointcut.label(invocation.getMethod(), target.getClass()));
    }
    advice.check(invocation.getArguments()[0]);
    return invocation.proceed();
  }

  /** Prepares a class that is called before the application has started, such as at its start. */
  private Map<Method, MethodAdvice> prepareOnCall(Class<?> targetClass, Method method) {
    synchronized (lock) {
      noted.add(targetClass);
    }
    prepareNoted();
    var methods = table.get(targetClass);
    if (methods == null) {
      throw new IllegalStateException(
          RulesPointcut.label(method, targetClass)
              + " is called while Muster is created, before its rules can be prepared; do not call"
              + " advised methods from the constructors of rules or of the beans they need");
    }
    return methods;
  }
}
