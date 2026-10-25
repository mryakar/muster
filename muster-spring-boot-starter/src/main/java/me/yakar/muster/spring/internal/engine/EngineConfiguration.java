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

import java.security.Principal;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Supplier;
import me.yakar.muster.Muster;
import me.yakar.muster.rule.Rule;
import me.yakar.muster.spi.MessageResolver;
import me.yakar.muster.spring.internal.Settings;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

/**
 * Builds the application's {@link Muster} bean from its rule beans. Not annotated with {@code
 * Configuration}, so that component scanning does not find it; Muster's auto-configuration imports
 * it.
 */
@Import(EngineConfiguration.SecurityConfiguration.class)
public final class EngineConfiguration {

  EngineConfiguration() {}

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
    var ruleBeans = RuleCatalog.ruleBeans(beanFactory);
    var ruleClasses = RuleCatalog.classes(ruleBeans);
    var ids = Arrays.stream(ruleClasses).map(ruleClass -> ruleClass.getAnnotation(Rule.class).id());
    Supplier<? extends @Nullable Principal> noCaller = () -> null;
    return Muster.builder()
        .rules(
            ruleClass -> RuleCatalog.instance(beanFactory, ruleBeans.get(ruleClass), ruleClass),
            ruleClasses)
        .disable(Settings.disabled(environment, ids.toList()).toArray(String[]::new))
        .mode(Settings.mode(environment))
        .caller(
            EngineConfiguration.<Supplier<? extends @Nullable Principal>>supplier(
                    beanFactory, Principal.class)
                .getIfAvailable(
                    () -> {
                      var security = securityCaller.getIfAvailable();
                      return security == null ? noCaller : security::current;
                    }))
        .locale(locale(beanFactory))
        .build();
  }

  @Bean
  @ConditionalOnMissingBean
  MessageResolver musterMessageResolver(ApplicationContext context) {
    // The context resolves messages through the message source of the application.
    return MessageResolver.ofTemplates(new MessageSourceTemplates(context));
  }

  /**
   * Returns where rules, and the messages of their violations, learn the locale of a call: a {@code
   * Supplier<Locale>} bean of the application, or {@link LocaleContextHolder}.
   */
  public static Supplier<Locale> locale(ConfigurableListableBeanFactory beanFactory) {
    return EngineConfiguration.<Supplier<Locale>>supplier(beanFactory, Locale.class)
        .getIfAvailable(() -> LocaleContextHolder::getLocale);
  }

  /**
   * Looks up a {@code Supplier} of exactly the given type. Unlike an injected {@code
   * ObjectProvider}, the lookup does not fall back to a raw {@code Supplier} or a {@code
   * Supplier<?>}, which supplies something else.
   */
  private static <T> ObjectProvider<T> supplier(
      ConfigurableListableBeanFactory beanFactory, Class<?> suppliedType) {
    return beanFactory.getBeanProvider(
        ResolvableType.forClassWithGenerics(Supplier.class, suppliedType));
  }

  /** Reads the caller from Spring Security when the application has it. */
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
