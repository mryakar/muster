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
import me.yakar.muster.spring.internal.actuator.ActuatorConfiguration;
import me.yakar.muster.spring.internal.advice.AdviceConfiguration;
import me.yakar.muster.spring.internal.engine.EngineConfiguration;
import me.yakar.muster.spring.internal.message.MessagesConfiguration;
import me.yakar.muster.spring.internal.observation.ObservationConfiguration;
import me.yakar.muster.spring.internal.problem.ProblemDetailsConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration that checks business rules in a Spring Boot application.
 *
 * <p>It registers the rule classes in the packages of the application as beans, builds a {@link
 * Muster} bean from the rule beans and the {@link me.yakar.muster.spi.CheckListener} beans, and
 * checks the rules of advised methods; see {@link Rules}. An advised method reads the values its
 * rules left from the {@link CheckedValues} bean. A {@code Muster}, {@code MessageResolver}, {@code
 * Supplier<Principal>} or {@code Supplier<Locale>} bean of the application replaces the starter's.
 * The {@code muster.*} properties, problem details, observations and the actuator endpoint are
 * described in the <a
 * href="https://mryakar.github.io/muster/muster/current/spring/configuration.html">documentation</a>.
 */
@AutoConfiguration(
    afterName =
        "org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration")
public final class MusterAutoConfiguration {

  MusterAutoConfiguration() {}

  /**
   * The values of the check of the advised method that runs on the calling thread.
   *
   * @return the bean
   */
  @Bean
  static CheckedValues musterCheckedValues() {
    return new CheckedValues();
  }

  /** The configurations of the starter, imported here so that its API docs do not name them. */
  @Import({
    EngineConfiguration.class,
    MessagesConfiguration.class,
    AdviceConfiguration.class,
    ProblemDetailsConfiguration.class,
    ObservationConfiguration.class,
    ActuatorConfiguration.class
  })
  static final class Configurations {

    private Configurations() {}
  }
}
