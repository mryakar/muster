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

import me.yakar.muster.Muster;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

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
  Muster muster(ConfigurableListableBeanFactory beanFactory) {
    return Muster.builder()
        .rules(ruleClass -> beanFactory.getBean(ruleClass), RuleCatalog.ruleClasses(beanFactory))
        .build();
  }
}
