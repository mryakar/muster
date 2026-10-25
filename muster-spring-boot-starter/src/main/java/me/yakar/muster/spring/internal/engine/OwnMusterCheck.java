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

import me.yakar.muster.spring.internal.Settings;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * Stops the start if the application defines its own {@code Muster} and still sets the properties
 * that configure the starter's, which would silently do nothing. It reads bean definitions only,
 * once every configuration class has registered its beans.
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
    var builtHere =
        beanFactory.containsBeanDefinition("muster")
            && EngineConfiguration.class
                .getName()
                .equals(beanFactory.getBeanDefinition("muster").getFactoryBeanName());
    if (!builtHere) {
      Settings.requireNoneForOwnMuster(requireNonNull(environment));
    }
  }
}
