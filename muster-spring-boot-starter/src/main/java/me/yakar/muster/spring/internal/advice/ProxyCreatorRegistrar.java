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

import me.yakar.muster.spring.internal.Settings;
import org.springframework.aop.config.AopConfigUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Makes sure that Spring creates proxies for Muster's advice, even where neither Spring Boot's AOP
 * support nor transaction management registers a proxy creator. Without one, rules would silently
 * not run. As Spring Boot does, it proxies classes unless {@code spring.aop.proxy-target-class} is
 * {@code false}.
 */
final class ProxyCreatorRegistrar implements ImportBeanDefinitionRegistrar {

  private final Environment environment;

  ProxyCreatorRegistrar(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void registerBeanDefinitions(
      AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
    AopConfigUtils.registerAutoProxyCreatorIfNecessary(registry);
    if (Settings.proxyTargetClass(environment)) {
      AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(registry);
    }
  }
}
