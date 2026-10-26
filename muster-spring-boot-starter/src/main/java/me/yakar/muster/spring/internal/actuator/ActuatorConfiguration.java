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
package me.yakar.muster.spring.internal.actuator;

import me.yakar.muster.Muster;
import me.yakar.muster.spring.internal.advice.AdviceTable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Defines the endpoint {@code muster} when the application has the Spring Boot actuator, and the
 * endpoint is enabled and exposed, as the actuator decides for its own endpoints. Not annotated
 * with {@code Configuration}, so that component scanning does not find it; Muster's
 * auto-configuration imports it.
 */
@ConditionalOnClass(
    name = {
      "org.springframework.boot.actuate.endpoint.annotation.Endpoint",
      "org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint"
    })
public final class ActuatorConfiguration {

  ActuatorConfiguration() {}

  @Bean
  @ConditionalOnAvailableEndpoint(MusterEndpoint.class)
  MusterEndpoint musterEndpoint(ObjectProvider<Muster> muster, AdviceTable musterAdviceTable) {
    return new MusterEndpoint(muster, musterAdviceTable);
  }
}
