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
package me.yakar.muster.spring.internal.observation;

import io.micrometer.observation.ObservationRegistry;
import me.yakar.muster.spi.CheckListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Observes checks and rules when the application has an {@code ObservationRegistry} bean. Not
 * annotated with {@code Configuration}, so that component scanning does not find it; Muster's
 * auto-configuration imports it, after Spring Boot's observation auto-configuration, so that the
 * registry it defines is seen.
 */
@ConditionalOnClass(name = "io.micrometer.observation.ObservationRegistry")
public final class ObservationConfiguration {

  ObservationConfiguration() {}

  @Bean
  @ConditionalOnBean(ObservationRegistry.class)
  CheckListener musterObservationListener(ObservationRegistry registry) {
    return new ObservationListener(registry);
  }
}
