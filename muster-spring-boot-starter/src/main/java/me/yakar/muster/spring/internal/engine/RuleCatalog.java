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
package me.yakar.muster.spring.internal.engine;

import static java.util.Objects.requireNonNull;

import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import me.yakar.muster.rule.BusinessRule;
import me.yakar.muster.rule.Rule;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.util.ClassUtils;

/** Reads the rule classes from the bean definitions, without creating the beans. */
final class RuleCatalog {

  private RuleCatalog() {}

  /**
   * Returns the names of the rule beans, by their classes.
   *
   * @throws IllegalStateException if a rule bean is not a singleton, its type has no {@code @Rule},
   *     or two rule beans have the same class
   */
  static Map<Class<?>, String> ruleBeans(ConfigurableListableBeanFactory beanFactory) {
    var beans = new LinkedHashMap<Class<?>, String>();
    for (var name : beanFactory.getBeanNamesForType(BusinessRule.class, true, false)) {
      if (!beanFactory.isSingleton(name)) {
        var definition = beanFactory.getMergedBeanDefinition(name);
        // A factory bean that is a singleton itself may still make a new rule each time.
        var how =
            definition.isSingleton()
                ? "is made anew each time by a factory bean"
                : "has the scope '" + definition.getScope() + "'";
        throw new IllegalStateException(
            "the rule bean '"
                + name
                + "' "
                + how
                + "; Muster creates each rule once and keeps it, so a rule bean must be a"
                + " singleton");
      }
      var type = declaredType(beanFactory, name);
      if (!type.isAnnotationPresent(Rule.class)) {
        throw new IllegalStateException(noRule(name, type));
      }
      var other = beans.putIfAbsent(type, name);
      if (other != null) {
        throw new IllegalStateException(
            "the rule beans '"
                + other
                + "' and '"
                + name
                + "' are both a "
                + type.getTypeName()
                + "; Muster creates each rule once, so define it once");
      }
    }
    return beans;
  }

  /** Returns the classes of the rule beans, as the builder of Muster takes them. */
  @SuppressWarnings("unchecked")
  static Class<? extends BusinessRule<?>>[] classes(Map<Class<?>, String> ruleBeans) {
    return ruleBeans.keySet().toArray(Class[]::new);
  }

  /**
   * Returns the rule bean of the given name.
   *
   * @throws IllegalStateException if the bean is a proxy based on interfaces, which is no instance
   *     of the rule class
   */
  static BusinessRule<?> instance(
      ConfigurableListableBeanFactory beanFactory, String name, Class<?> ruleClass) {
    var bean = beanFactory.getBean(name, BusinessRule.class);
    if (Proxy.isProxyClass(bean.getClass())) {
      throw new IllegalStateException(
          "the rule bean '"
              + name
              + "' is a proxy based on interfaces, which is no "
              + ruleClass.getTypeName()
              + "; let Spring proxy classes, as spring.aop.proxy-target-class=true does");
    }
    return bean;
  }

  /**
   * Returns the type a bean is declared with, such as the return type of its {@code @Bean} method,
   * rather than the class of an instance that may already exist, so that the outcome does not
   * depend on the order in which beans are created. A factory bean is known by what it makes.
   */
  private static Class<?> declaredType(ConfigurableListableBeanFactory beanFactory, String name) {
    var type = requireNonNull(beanFactory.getType(name, false));
    if (beanFactory.containsBeanDefinition(name)) {
      var declared = beanFactory.getMergedBeanDefinition(name).getResolvableType().resolve(type);
      if (BusinessRule.class.isAssignableFrom(declared)) {
        type = declared;
      }
    }
    return ClassUtils.getUserClass(type);
  }

  private static String noRule(String name, Class<?> type) {
    // Interfaces are abstract too.
    if (Modifier.isAbstract(type.getModifiers())) {
      return "the rule bean '"
          + name
          + "' is declared as "
          + type.getTypeName()
          + ", which has no @Rule; declare the rule's own class as the type of its @Bean method";
    }
    return "the rule bean '"
        + name
        + "' is a "
        + type.getTypeName()
        + ", which has no @Rule; give the rule an id with @Rule";
  }
}
