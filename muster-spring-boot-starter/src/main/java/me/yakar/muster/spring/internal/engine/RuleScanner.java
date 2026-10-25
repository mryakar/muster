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

import java.lang.System.Logger.Level;
import java.util.HashSet;
import me.yakar.muster.rule.BusinessRule;
import me.yakar.muster.rule.Rule;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.annotation.AnnotationScopeMetadataResolver;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

/**
 * Registers the rule classes in the packages of the application as beans, unless a bean of the
 * class is already defined. Their annotations, such as {@code @Lazy}, {@code @DependsOn},
 * {@code @Scope} and conditions, apply as they do to components. It runs after the configuration
 * classes of the application have defined their beans. A rule bean is named after its class, so it
 * cannot clash with a bean of the application.
 */
final class RuleScanner
    implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, ResourceLoaderAware {

  private static final System.Logger LOGGER = System.getLogger("me.yakar.muster");

  private @Nullable Environment environment;
  private @Nullable ResourceLoader resourceLoader;

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void setResourceLoader(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }

  @Override
  public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
    // In an application context, the registry is the bean factory.
    var beanFactory = (ConfigurableListableBeanFactory) registry;
    if (!AutoConfigurationPackages.has(beanFactory)) {
      return;
    }
    // As Spring matches beans by type at this stage: singletons registered without a definition
    // count, abstract definitions and factory beans that are not created yet do not.
    var defined = new HashSet<String>();
    for (var name : beanFactory.getBeanNamesForType(BusinessRule.class, true, false)) {
      // A singleton may be a proxy, such as one made with CGLIB, of the rule class.
      defined.add(
          ClassUtils.getUserClass(requireNonNull(beanFactory.getType(name, false))).getName());
    }
    // With the registry, conditions such as @ConditionalOnBean apply as in component scanning.
    var scanner =
        new ClassPathScanningCandidateComponentProvider(false, requireNonNull(environment)) {
          @Override
          protected BeanDefinitionRegistry getRegistry() {
            return registry;
          }
        };
    scanner.setResourceLoader(resourceLoader);
    var annotated = new AnnotationTypeFilter(Rule.class);
    var rule = new AssignableTypeFilter(BusinessRule.class);
    scanner.addIncludeFilter(
        (reader, factory) -> annotated.match(reader, factory) && rule.match(reader, factory));
    var scopes = new AnnotationScopeMetadataResolver();
    for (var basePackage : AutoConfigurationPackages.get(beanFactory)) {
      for (var candidate : scanner.findCandidateComponents(basePackage)) {
        var className = requireNonNull(candidate.getBeanClassName());
        if (defined.add(className)) {
          candidate.setScope(scopes.resolveScopeMetadata(candidate).getScopeName());
          // @Lazy, @DependsOn, @Primary and the like, as in component scanning.
          AnnotationConfigUtils.processCommonDefinitionAnnotations(
              (AnnotatedBeanDefinition) candidate);
          registry.registerBeanDefinition(className, candidate);
          LOGGER.log(Level.DEBUG, () -> "Registered the rule " + className + " as a bean");
        }
      }
    }
  }
}
