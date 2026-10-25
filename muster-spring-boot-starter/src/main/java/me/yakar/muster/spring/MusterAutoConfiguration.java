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
import me.yakar.muster.spi.MessageResolver;
import me.yakar.muster.spring.internal.advice.AdviceConfiguration;
import me.yakar.muster.spring.internal.engine.EngineConfiguration;
import me.yakar.muster.spring.internal.problem.ProblemDetailsConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * Auto-configuration that checks business rules in a Spring Boot application.
 *
 * <p>It builds a {@link Muster} bean from the rule beans, registers the rule classes in the
 * packages of the application as beans, and checks the rules of advised methods; see {@link Rules}.
 * It reads the caller from Spring Security and the locale from {@link LocaleContextHolder},
 * resolves messages through a {@link MessageResolver} bean backed by the message source of the
 * application, and, in a Spring MVC application, answers a {@link
 * me.yakar.muster.BusinessRuleViolationException} with problem details (RFC 9457).
 *
 * <p>A {@code Muster}, {@code MessageResolver}, {@code Supplier<Principal>} or {@code
 * Supplier<Locale>} bean of the application replaces the starter's; an application with a {@code
 * Muster} of its own does not start if it sets {@code muster.rules} or {@code muster.mode}. The
 * {@code muster.*} properties and the details are in the <a
 * href="https://github.com/mryakar/muster#usage-with-spring-boot">README</a>.
 */
@AutoConfiguration
@Import({EngineConfiguration.class, AdviceConfiguration.class, ProblemDetailsConfiguration.class})
public final class MusterAutoConfiguration {

  MusterAutoConfiguration() {}
}
