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

import static java.lang.System.Logger.Level.WARNING;
import static java.util.Comparator.comparing;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import me.yakar.muster.Muster;
import me.yakar.muster.spring.internal.selfinvocation.SelfInvocation;
import me.yakar.muster.spring.internal.selfinvocation.SelfInvocationMode;
import me.yakar.muster.spring.internal.selfinvocation.SelfInvocationScanner;
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
 * reads the {@link AdviceTable table} without a lock: two map lookups.
 */
final class RulesInterceptor implements MethodInterceptor, SmartInitializingSingleton {

  private static final System.Logger LOGGER = System.getLogger("me.yakar.muster");

  private final ObjectProvider<Muster> muster;
  private final ObjectProvider<TransactionGuard> transactions;
  private final SelfInvocationMode selfInvocation;

  private final Object lock = new Object();

  /** The classes the pointcut advises. Guarded by {@link #lock}. */
  private final Set<Class<?>> noted = new LinkedHashSet<>();

  private final AdviceTable table;

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
   * Prepares the noted classes that are not prepared yet, and scans each for calls of its advised
   * methods on {@code this}. Muster, the guard and its transaction test are resolved before the
   * lock is taken: creating them may wait for a bean that another thread creates, and that thread
   * may note a class meanwhile.
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
        var selfInvocations = new ArrayList<SelfInvocation>();
        // Preparing a class may note another, so the pass goes over a copy until none is left.
        for (var unprepared = unprepared(); !unprepared.isEmpty(); unprepared = unprepared()) {
          for (var targetClass : unprepared) {
            var advice = adviceOf(muster, resolved, targetClass);
            var found =
                selfInvocation == SelfInvocationMode.OFF
                    ? List.<SelfInvocation>of()
                    : SelfInvocationScanner.find(targetClass, labels(advice));
            selfInvocations.addAll(found);
            // A class whose calls fail the pass stays unprepared, so that creating a bean of it
            // later fails again.
            if (found.isEmpty() || selfInvocation != SelfInvocationMode.FAIL) {
              table.put(targetClass, advice);
            }
          }
          requireNone(selfInvocations);
        }
        warn(selfInvocations);
      } finally {
        preparing = false;
      }
      checkOrder = guard != null && !orderChecked && !table.isEmpty();
      orderChecked |= checkOrder;
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
   * Fails on the calls of advised methods on {@code this} found so far, naming them all in the
   * order of their callers, if the configuration says so.
   *
   * @throws IllegalStateException in {@link SelfInvocationMode#FAIL} mode, if any was found
   */
  private void requireNone(List<SelfInvocation> selfInvocations) {
    if (selfInvocation == SelfInvocationMode.FAIL && !selfInvocations.isEmpty()) {
      throw new IllegalStateException(
          "Muster found calls of advised methods through this, which pass no proxy and check no"
              + " rules: "
              + sorted(selfInvocations).stream()
                  .map(Object::toString)
                  .collect(Collectors.joining("; "))
              + ". Call the method through its bean: inject the bean itself, with @Lazy or an"
              + " ObjectProvider, or move the method to another bean; or set"
              + " muster.self-invocation=warn to log such calls instead");
    }
  }

  /**
   * Warns of each call of an advised method on {@code this} found in a pass, in the order of their
   * callers.
   */
  private static void warn(List<SelfInvocation> selfInvocations) {
    for (var found : sorted(selfInvocations)) {
      LOGGER.log(
          WARNING,
          found + ", which passes no proxy and checks no rules; call the method through its bean");
    }
  }

  private static List<SelfInvocation> sorted(List<SelfInvocation> selfInvocations) {
    return selfInvocations.stream()
        .sorted(comparing(SelfInvocation::caller).thenComparing(SelfInvocation::callee))
        .toList();
  }

  /** Returns the noted classes that are not prepared yet. Called under {@link #lock}. */
  private List<Class<?>> unprepared() {
    return noted.stream().filter(targetClass -> !table.contains(targetClass)).toList();
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
    var methods = table.get(target.getClass());
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
    var methods = table.get(targetClass);
    if (methods == null) {
      throw new IllegalStateException(
          AdvisedMethods.label(method, targetClass)
              + " is called while Muster is created, before its rules can be prepared; do not call"
              + " advised methods from the constructors of rules or of the beans they need");
    }
    return methods;
  }
}
