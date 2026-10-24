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

import java.security.Principal;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Supplier;
import me.yakar.muster.Muster;
import me.yakar.muster.rule.Rule;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

/**
 * Builds {@link Muster} from the business rules of a Spring Boot application.
 *
 * <p>The rules are the beans that implement {@link me.yakar.muster.rule.BusinessRule}. Rule classes
 * with {@link me.yakar.muster.rule.Rule @Rule} in the packages of the application, the packages
 * that {@code @SpringBootApplication} scans for entities as well, become beans without
 * {@code @Component}; a rule elsewhere joins once it is a bean, for example through {@code @Bean}
 * or {@code @Import}. Every rule bean must be a singleton, since Muster creates each rule once and
 * keeps it.
 *
 * <p>Configuration switches rules off and sets the mode:
 *
 * <ul>
 *   <li>{@code muster.rules.<rule id>.enabled=false} switches a rule off, as {@link
 *       Muster.Builder#disable} does; as an environment variable, {@code
 *       MUSTER_RULES_VEHICLE_NAMEUNIQUE_ENABLED=false} switches {@code vehicle.name-unique} off. A
 *       key under {@code muster.rules} that switches no rule, such as a misspelled id, stops the
 *       application.
 *   <li>{@code muster.mode} is {@code fail-fast}, the default, or {@code collect-all}.
 * </ul>
 *
 * <p>Rules learn who makes a call and in which locale:
 *
 * <ul>
 *   <li>The caller is the authentication of Spring Security, if the application has Spring
 *       Security, read through its {@code SecurityContextHolderStrategy} bean if it has one; an
 *       anonymous or not authenticated call has no caller. A bean of type {@code
 *       Supplier<Principal>} replaces it.
 *   <li>The locale is the one {@link LocaleContextHolder} holds, as Spring MVC sets it for a
 *       request, and otherwise the default locale of the JVM. A bean of type {@code
 *       Supplier<Locale>} replaces it.
 * </ul>
 *
 * <p>Define a {@link Muster} bean of your own to build Muster yourself.
 */
@AutoConfiguration
public final class MusterAutoConfiguration {

  MusterAutoConfiguration() {}

  @Bean
  static RuleScanner musterRuleScanner() {
    return new RuleScanner();
  }

  @Bean
  @ConditionalOnMissingBean
  Muster muster(
      ConfigurableListableBeanFactory beanFactory,
      Environment environment,
      ObjectProvider<SecurityCaller> securityCaller) {
    var ruleClasses = RuleCatalog.ruleClasses(beanFactory);
    var ids = Arrays.stream(ruleClasses).map(ruleClass -> ruleClass.getAnnotation(Rule.class).id());
    Supplier<? extends @Nullable Principal> noCaller = () -> null;
    return Muster.builder()
        .rules(ruleClass -> beanFactory.getBean(ruleClass), ruleClasses)
        .disable(RuleSwitches.disabled(environment, ids.toList()).toArray(String[]::new))
        .mode(RuleSwitches.mode(environment))
        .caller(
            this.<Supplier<? extends @Nullable Principal>>supplier(beanFactory, Principal.class)
                .getIfAvailable(
                    () -> {
                      var security = securityCaller.getIfAvailable();
                      return security == null ? noCaller : security::current;
                    }))
        .locale(
            this.<Supplier<Locale>>supplier(beanFactory, Locale.class)
                .getIfAvailable(() -> LocaleContextHolder::getLocale))
        .build();
  }

  /**
   * Looks up a {@code Supplier} of exactly the given type. Unlike an injected {@code
   * ObjectProvider}, the lookup does not fall back to a raw {@code Supplier} or a {@code
   * Supplier<?>}, which supplies something else.
   */
  private <T> ObjectProvider<T> supplier(
      ConfigurableListableBeanFactory beanFactory, Class<?> suppliedType) {
    return beanFactory.getBeanProvider(
        ResolvableType.forClassWithGenerics(Supplier.class, suppliedType));
  }

  /** Reads the caller from Spring Security when the application has it. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
  static class SecurityConfiguration {

    @Bean
    SecurityCaller musterSecurityCaller(ObjectProvider<SecurityContextHolderStrategy> strategy) {
      var own = strategy.getIfAvailable();
      return new SecurityCaller(
          own == null ? SecurityContextHolder::getContextHolderStrategy : () -> own);
    }
  }
}
