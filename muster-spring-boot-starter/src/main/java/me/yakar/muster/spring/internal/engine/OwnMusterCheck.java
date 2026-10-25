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

import me.yakar.muster.Muster;
import me.yakar.muster.spring.internal.Settings;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * Stops the start if the application defines its own {@code Muster} and still sets the properties
 * that configure the starter's, which would silently do nothing. It reads bean definitions only,
 * once every configuration class has registered its beans, and only those of its own bean factory:
 * a child context that uses the {@code Muster} of its parent defines none.
 *
 * <p>A definition is the starter's when {@link EngineConfiguration} declares its factory method,
 * whatever the names of the bean and of the configuration, which an application may choose.
 */
final class OwnMusterCheck implements BeanFactoryPostProcessor, EnvironmentAware {

  // Set through Aware rather than injected: resolving a parameter of the bean method by type would
  // create factory beans early to learn what they make.
  private @Nullable Environment environment;

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    for (var name : beanFactory.getBeanNamesForType(Muster.class, true, false)) {
      if (!builtByStarter(beanFactory, name)) {
        Settings.requireNoneForOwnMuster(requireNonNull(environment));
        return;
      }
    }
  }

  /** A singleton registered without a definition is the application's. */
  private static boolean builtByStarter(ConfigurableListableBeanFactory beanFactory, String name) {
    if (!beanFactory.containsBeanDefinition(name)) {
      return false;
    }
    var factoryMethod =
        beanFactory.getBeanDefinition(name) instanceof AnnotatedBeanDefinition annotated
            ? annotated.getFactoryMethodMetadata()
            : null;
    return factoryMethod != null
        && EngineConfiguration.class.getName().equals(factoryMethod.getDeclaringClassName());
  }
}
