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
package me.yakar.muster.spring;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
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
 * <p>The pointcut notes each class it advises when Spring creates a proxy for a bean. The bindings
 * of a noted class are prepared once all singletons exist, so that a rule may depend on an advised
 * service, or at once for a bean created later, before its proxy is used; a wrong binding stops the
 * application then. A call only reads them: two lookups in maps that take no lock.
 */
final class RulesInterceptor implements MethodInterceptor, SmartInitializingSingleton {

  private final ObjectProvider<Muster> muster;

  /** The classes the pointcut advises. */
  private final Set<Class<?>> noted = ConcurrentHashMap.newKeySet();

  /**
   * The advice of each method, by the class of the target and then by the method a proxy calls: the
   * method of the class or, for an interface-based proxy, the method of the interface.
   */
  private final ConcurrentMap<Class<?>, Map<Method, MethodAdvice>> prepared =
      new ConcurrentHashMap<>();

  private volatile boolean started;

  RulesInterceptor(ObjectProvider<Muster> muster) {
    this.muster = muster;
  }

  /** Notes a class the pointcut advises; once singletons exist, prepares it at once. */
  void note(Class<?> targetClass) {
    // Written before started is read, as afterSingletonsInstantiated writes started before it
    // reads noted: one of them prepares a class noted while the application starts. Preparing is
    // done once per class.
    noted.add(targetClass);
    if (started) {
      prepare(targetClass);
    }
  }

  @Override
  public void afterSingletonsInstantiated() {
    started = true;
    noted.forEach(this::prepare);
  }

  private void prepare(Class<?> targetClass) {
    // The function reads the class and builds bindings; it never touches the map.
    prepared.computeIfAbsent(targetClass, this::adviceOf);
  }

  private Map<Method, MethodAdvice> adviceOf(Class<?> targetClass) {
    var muster = this.muster.getObject();
    var advice = new HashMap<Method, MethodAdvice>();
    for (var method : ReflectionUtils.getUniqueDeclaredMethods(targetClass)) {
      if (AdvisedMethods.advised(method, targetClass)) {
        var methodAdvice = MethodAdvice.of(muster, method, targetClass);
        advice.put(method, methodAdvice);
        for (var type : ClassUtils.getAllInterfacesForClassAsSet(targetClass)) {
          var declared =
              ClassUtils.getMethodIfAvailable(type, method.getName(), method.getParameterTypes());
          if (declared != null) {
            advice.put(declared, methodAdvice);
          }
        }
      }
    }
    return Map.copyOf(advice);
  }

  @Override
  public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
    var target = invocation.getThis();
    var methods = target == null ? null : prepared.get(target.getClass());
    var advice = methods == null ? null : methods.get(invocation.getMethod());
    if (advice == null) {
      throw new IllegalStateException(
          "Muster has not prepared the rules of " + invocation.getMethod().toGenericString());
    }
    advice.check(invocation.getArguments()[0]);
    return invocation.proceed();
  }

  /** What Muster does before an advised method runs. */
  private record MethodAdvice(String label, Binding<Object> binding) {

    @SuppressWarnings("unchecked")
    static MethodAdvice of(Muster muster, Method method, Class<?> targetClass) {
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
      return new MethodAdvice(label, builder.build());
    }

    void check(@Nullable Object input) {
      binding.check(input);
    }
  }
}
