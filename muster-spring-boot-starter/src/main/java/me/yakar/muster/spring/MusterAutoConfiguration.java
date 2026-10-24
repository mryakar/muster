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
import me.yakar.muster.spi.MessageResolver;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Role;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;

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
 *   <li>{@code muster.transaction.reorder-advisor=false} keeps Muster from moving the transaction
 *       advice ahead of its own; see {@link Rules}.
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
 * <p>Messages of violations come from the message source of the application, through a {@link
 * MessageResolver} bean: the message of a violation is the template its code names, such as {@code
 * vehicle.name-taken=The name {name} is taken} in {@code messages.properties}, with its {@code
 * {name}} placeholders filled from the client arguments of the violation; without a template, the
 * message is the code. Inject the bean to resolve messages in an exception handler of your own;
 * define a {@code MessageResolver} bean of your own to resolve them from elsewhere. Spring Boot's
 * message source falls back to the default locale of the JVM when it has no messages for the locale
 * of a request, so a Turkish server answers a French client in Turkish; set {@code
 * spring.messages.fallback-to-system-locale=false} to answer from {@code messages.properties}
 * instead. A message source that formats every message with {@link java.text.MessageFormat}, as
 * {@code spring.messages.always-use-message-format=true} makes it, stops the application, since
 * {@code MessageFormat} rejects the placeholders.
 *
 * <p>Define a {@link Muster} bean of your own to build Muster yourself.
 */
@AutoConfiguration
@Import(ProxyCreatorRegistrar.class)
public final class MusterAutoConfiguration {

  MusterAutoConfiguration() {}

  @Bean
  static RuleScanner musterRuleScanner() {
    return new RuleScanner();
  }

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  static RulesInterceptor musterRulesInterceptor(
      ObjectProvider<Muster> muster, ObjectProvider<TransactionGuard> transactions) {
    return new RulesInterceptor(muster, transactions);
  }

  // Infrastructure, so that the proxy creator that transaction management registers applies it.
  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  static RulesAdvisor musterRulesAdvisor(RulesInterceptor musterRulesInterceptor) {
    return new RulesAdvisor(musterRulesInterceptor);
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
        .disable(RuleSwitches.disabled(environment, ids.toList()).toArray(String[]::new))
        .mode(RuleSwitches.mode(environment))
        .caller(
            MusterAutoConfiguration.<Supplier<? extends @Nullable Principal>>supplier(
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
  static Supplier<Locale> locale(ConfigurableListableBeanFactory beanFactory) {
    return MusterAutoConfiguration.<Supplier<Locale>>supplier(beanFactory, Locale.class)
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

  /** Keeps rules inside the transaction when the application has Spring's transactions. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(
      name =
          "org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor")
  static class TransactionConfiguration {

    @Bean
    static TransactionOrdering musterTransactionOrdering() {
      return new TransactionOrdering();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static TransactionGuard musterTransactionGuard(
        ObjectProvider<BeanFactoryTransactionAttributeSourceAdvisor> advisor) {
      return new TransactionGuard(advisor);
    }
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
