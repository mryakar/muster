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
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import me.yakar.muster.Binding;
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
 * <p>The pointcut notes each class it advises when Spring creates a proxy for a bean. The noted
 * classes are prepared once all singletons exist, so that a rule may depend on an advised service;
 * a class noted later, such as that of a lazy bean, is prepared when its proxy is created, and a
 * class called before the application has started is prepared on that call. A wrong binding fails
 * then. Preparing takes a lock and may create Muster; a class noted while Muster is created joins
 * the same pass. A call only reads what is prepared: two lookups in maps that take no lock.
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
   * Whether the holder of {@link #lock} is preparing classes, so that a class it notes on the way
   * joins its pass. Guarded by {@link #lock}, which the same thread may take again.
   */
  private boolean preparing;

  /** Whether the order of the transaction advice has been checked. Guarded by {@link #lock}. */
  private boolean orderChecked;

  RulesInterceptor(ObjectProvider<Muster> muster, ObjectProvider<TransactionGuard> transactions) {
    this.muster = muster;
    this.transactions = transactions;
  }

  /** Notes a class the pointcut advises; once the application has started, prepares it at once. */
  void note(Class<?> targetClass) {
    synchronized (lock) {
      noted.add(targetClass);
      if (started) {
        prepareNoted();
      }
    }
  }

  @Override
  public void afterSingletonsInstantiated() {
    synchronized (lock) {
      started = true;
      prepareNoted();
    }
  }

  /**
   * Prepares the noted classes that are not prepared yet. Creating Muster or a bean it needs may
   * note more classes on this thread; they join the same pass rather than prepare re-entrantly.
   */
  private void prepareNoted() {
    if (preparing) {
      return;
    }
    preparing = true;
    try {
      var muster = this.muster.getObject();
      var guard = transactions.getIfAvailable();
      for (var pending = unprepared(); !pending.isEmpty(); pending = unprepared()) {
        for (var targetClass : pending) {
          prepared.put(targetClass, adviceOf(muster, guard, targetClass));
        }
      }
      if (guard != null && !orderChecked && !prepared.isEmpty()) {
        orderChecked = true;
        guard.checkOrder(RulesAdvisor.ORDER);
      }
    } finally {
      preparing = false;
    }
  }

  private List<Class<?>> unprepared() {
    return noted.stream().filter(targetClass -> !prepared.containsKey(targetClass)).toList();
  }

  /**
   * Maps every method that a proxy may call for an advised method of the class to the advice of the
   * method that the call runs.
   */
  private static Map<Method, MethodAdvice> adviceOf(
      Muster muster, @Nullable TransactionGuard guard, Class<?> targetClass) {
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
      if (AdvisedMethods.advised(method, targetClass)) {
        var specific = AdvisedMethods.specific(method, targetClass);
        var methodAdvice = bySpecific.get(specific);
        if (methodAdvice == null) {
          methodAdvice = MethodAdvice.of(muster, guard, specific, targetClass);
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
          "Muster has not prepared the rules of " + invocation.getMethod().toGenericString());
    }
    advice.check(invocation.getArguments()[0]);
    return invocation.proceed();
  }

  /** Prepares a class that is called before the application has started, such as at its start. */
  private Map<Method, MethodAdvice> prepareOnCall(Class<?> targetClass, Method method) {
    synchronized (lock) {
      noted.add(targetClass);
      prepareNoted();
    }
    var methods = prepared.get(targetClass);
    if (methods == null) {
      throw new IllegalStateException(
          method.toGenericString()
              + " is called while Muster is created, before its rules can be prepared; do not call"
              + " advised methods from the constructors of rules or of the beans they need");
    }
    return methods;
  }

  /** What Muster does before an advised method runs. */
  private record MethodAdvice(
      String label, Binding<Object> binding, @Nullable TransactionGuard transaction) {

    @SuppressWarnings("unchecked")
    static MethodAdvice of(
        Muster muster, @Nullable TransactionGuard guard, Method method, Class<?> targetClass) {
      var label = AdvisedMethods.label(method, targetClass);
      var modifiers = method.getModifiers();
      if (!Modifier.isPublic(modifiers)) {
        throw new IllegalStateException(
            label + " has rules but is not public; a proxy calls public methods only");
      }
      if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
        throw new IllegalStateException(
            label
                + " has rules but is "
                + (Modifier.isStatic(modifiers) ? "static" : "final")
                + ", so a proxy cannot check them before it runs");
      }
      var count = method.getParameterCount();
      if (count != 1) {
        throw new IllegalStateException(
            label
                + (count == 0
                    ? " has rules but no parameter; rules check the only parameter of a method"
                    : " takes "
                        + count
                        + " parameters; a method with rules takes one, so pass a command object"
                        + " that holds them, such as a record"));
      }
      var builder = muster.binding((Class<Object>) method.getParameterTypes()[0]).name(label);
      var rules = AdvisedMethods.rules(method);
      if (rules != null) {
        builder.rules(rules.value()).exclude(rules.exclude());
        if (rules.mode().length > 1) {
          throw new IllegalStateException(
              "@Rules on "
                  + label
                  + " names "
                  + rules.mode().length
                  + " modes; give one, or none for the mode of Muster");
        }
        if (rules.mode().length == 1) {
          builder.mode(rules.mode()[0]);
        }
      }
      var transaction =
          guard != null && guard.requiresTransaction(method, targetClass) ? guard : null;
      return new MethodAdvice(label, builder.build(), transaction);
    }

    void check(@Nullable Object input) {
      if (transaction != null) {
        transaction.requireTransaction(label);
      }
      binding.check(input);
    }
  }
}
