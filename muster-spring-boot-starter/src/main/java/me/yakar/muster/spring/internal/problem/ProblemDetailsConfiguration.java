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
package me.yakar.muster.spring.internal.problem;

import me.yakar.muster.spi.MessageResolver;
import me.yakar.muster.spring.internal.Settings;
import me.yakar.muster.spring.internal.engine.EngineConfiguration;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Answers violations with problem details in a Spring MVC application. Not annotated with {@code
 * Configuration}, so that component scanning does not find it; Muster's auto-configuration imports
 * it.
 */
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
public final class ProblemDetailsConfiguration {

  ProblemDetailsConfiguration() {}

  @Bean
  static ViolationHandlerRemover musterViolationHandlerRemover() {
    return new ViolationHandlerRemover();
  }

  @Bean(ViolationHandlerRemover.HANDLER)
  ViolationExceptionHandler musterViolationExceptionHandler(
      MessageResolver messages,
      ConfigurableListableBeanFactory beanFactory,
      Environment environment) {
    return new ViolationExceptionHandler(
        messages, EngineConfiguration.locale(beanFactory), Settings.mixedStatus(environment));
  }
}
