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
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import me.yakar.muster.Muster;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Checks the input of an advised method against its rules, then calls the method.
 *
 * <p>Lifecycle: the pointcut notes each class it advises when Spring proxies a bean. Noted classes
 * are prepared once all singletons exist, so a rule may depend on an advised service; a class noted
 * later, such as a lazy bean's, when it is noted; a class called during startup, on that call. A
 * wrong binding fails when its class is prepared.
 *
 * <p>Concurrency: a pass gets the {@code Muster} before it takes {@link #lock}, so that no bean is
 * created under the lock. A class that the same thread notes while it gets the {@code Muster} or
 * prepares a class, such as when preparing creates an advised bean, starts no pass of its own: the
 * running pass prepares it, since the pass repeats until no noted class is left unprepared. A call
 * reads {@link #prepared} without a lock: two map lookups.
 */
final class RulesInterceptor implements MethodInterceptor, SmartInitializingSingleton {

  private final ObjectProvider<Muster> muster;
  private final ObjectProvider<TransactionGuard> transactions;

  private final Object lock = new Object();

  /** The classes the pointcut advises. Guarded by {@link #lock}. */
  private final Set<Class<?>> noted = new LinkedHashSet<>();

  /**
   * The advice of each method, by the class of the target and then by the method a proxy calls: the
   * method of the class, a bridge, or a method of an interface or a base class. Written under
   * {@link #lock}; read without it.
   */
  private final ConcurrentMap<Class<?>, Map<Method, MethodAdvice>> prepared =
      new ConcurrentHashMap<>();

  /** Guarded by {@link #lock}. */
  private boolean started;

  /**
   * Set while a pass prepares classes, so that a class its thread notes meanwhile waits for the
   * pass rather than change {@link #noted} under it. Guarded by {@link #lock}.
   */
  private boolean preparing;

  /**
   * Set while this thread gets the {@code Muster} or the guard for a pass, so that a class it notes
   * meanwhile joins that pass.
   */
  private final ThreadLocal<Boolean> resolving = new ThreadLocal<>();

  /** Whether the order of the transaction advice has been checked. Guarded by {@link #lock}. */
  private boolean orderChecked;

  RulesInterceptor(ObjectProvider<Muster> muster, ObjectProvider<TransactionGuard> transactions) {
    this.muster = muster;
    this.transactions = transactions;
  }

  /** Notes a class the pointcut advises; once the application has started, prepares it at once. */
  void note(Class<?> targetClass) {
    // The pointcut notes a class again for every new proxy, such as of a prototype bean.
    if (prepared.containsKey(targetClass)) {
      return;
    }
    boolean prepare;
    synchronized (lock) {
      noted.add(targetClass);
      prepare = started;
    }
    if (prepare) {
      prepareNoted();
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
   * Prepares the noted classes that are not prepared yet. Muster, the guard and its transaction
   * test are resolved before the lock is taken: creating them may wait for a bean that another
   * thread creates, and that thread may note a class meanwhile.
   */
  private void prepareNoted() {
    if (resolving.get() != null) {
      return;
    }
    synchronized (lock) {
      if (preparing) {
        return;
      }
    }
    Muster muster;
    @Nullable TransactionGuard guard;
    TransactionGuard.@Nullable Resolved resolved;
    resolving.set(Boolean.TRUE);
    try {
      muster = this.muster.getObject();
      guard = transactions.getIfAvailable();
      resolved = guard == null ? null : guard.resolve();
    } finally {
      resolving.remove();
    }
    boolean checkOrder;
    synchronized (lock) {
      preparing = true;
      try {
        // Preparing a class may note another, so the pass goes over a copy until none is left.
        for (var unprepared = unprepared(); !unprepared.isEmpty(); unprepared = unprepared()) {
          for (var targetClass : unprepared) {
            prepared.put(targetClass, adviceOf(muster, resolved, targetClass));
          }
        }
      } finally {
        preparing = false;
      }
      checkOrder = guard != null && !orderChecked && !prepared.isEmpty();
      orderChecked |= checkOrder;
    }
    if (checkOrder) {
      guard.checkOrder(RulesAdvisor.ORDER);
    }
  }

  /** Returns the noted classes that are not prepared yet. Called under {@link #lock}. */
  private List<Class<?>> unprepared() {
    return noted.stream().filter(targetClass -> !prepared.containsKey(targetClass)).toList();
  }

  /**
   * Maps every method that a proxy may call for an advised method of the class to the advice of the
   * method that the call runs.
   */
  private static Map<Method, MethodAdvice> adviceOf(
      Muster muster, TransactionGuard.@Nullable Resolved transactions, Class<?> targetClass) {
    var candidates = new LinkedHashSet<Method>();
    if (!Proxy.isProxyClass(targetClass)) {
      candidates.addAll(List.of(ReflectionUtils.getAllDeclaredMethods(targetClass)));
    }
    for (var type : ClassUtils.getAllInterfacesForClassAsSet(targetClass)) {
      candidates.addAll(List.of(type.getMethods()));
    }
    var bySpecific = new HashMap<Method, MethodAdvice>();
    var advice = new HashMap<Method, MethodAdvice>();
    for (var method : candidates) {
      var specific = AdvisedMethods.advised(method, targetClass);
      if (specific != null) {
        var methodAdvice = bySpecific.get(specific);
        if (methodAdvice == null) {
          methodAdvice = MethodAdvice.of(muster, transactions, specific, targetClass);
          bySpecific.put(specific, methodAdvice);
        }
        advice.put(method, methodAdvice);
      }
    }
    return Map.copyOf(advice);
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
    var methods = prepared.get(target.getClass());
    if (methods == null) {
      methods = prepareOnCall(target.getClass(), invocation.getMethod());
    }
    var advice = methods.get(invocation.getMethod());
    if (advice == null) {
      throw new IllegalStateException(
          "Muster has not prepared the rules of "
              + AdvisedMethods.label(invocation.getMethod(), target.getClass()));
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
    var methods = prepared.get(targetClass);
    if (methods == null) {
      throw new IllegalStateException(
          AdvisedMethods.label(method, targetClass)
              + " is called while Muster is created, before its rules can be prepared; do not call"
              + " advised methods from the constructors of rules or of the beans they need");
    }
    return methods;
  }
}
