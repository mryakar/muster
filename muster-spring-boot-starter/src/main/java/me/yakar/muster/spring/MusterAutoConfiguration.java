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

import java.util.Arrays;
import me.yakar.muster.Muster;
import me.yakar.muster.rule.Rule;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

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
  Muster muster(ConfigurableListableBeanFactory beanFactory, Environment environment) {
    var ruleClasses = RuleCatalog.ruleClasses(beanFactory);
    var ids = Arrays.stream(ruleClasses).map(ruleClass -> ruleClass.getAnnotation(Rule.class).id());
    return Muster.builder()
        .rules(ruleClass -> beanFactory.getBean(ruleClass), ruleClasses)
        .disable(RuleSwitches.disabled(environment, ids.toList()).toArray(String[]::new))
        .mode(RuleSwitches.mode(environment))
        .build();
  }
}
