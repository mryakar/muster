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

import me.yakar.muster.spi.MessageResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Resolves the messages of violations through the message source of the application. Not annotated
 * with {@code Configuration}, so that component scanning does not find it; Muster's
 * auto-configuration imports it, and so does a {@code WebMvcTest}, which answers violations with
 * problem details without the rest of Muster.
 */
public final class MessagesConfiguration {

  MessagesConfiguration() {}

  @Bean
  @ConditionalOnMissingBean
  MessageResolver musterMessageResolver(ApplicationContext context) {
    return MessageResolver.ofTemplates(new MessageSourceTemplates(context));
  }
}
