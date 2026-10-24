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

import java.util.Locale;
import java.util.Optional;
import java.util.function.BiFunction;
import org.springframework.context.MessageSource;
import org.springframework.context.support.DefaultMessageSourceResolvable;

/**
 * Looks up the template for a code in the message source of the application, as it is written, so
 * that Muster fills its {@code {name}} placeholders.
 *
 * <p>A message source returns a message without arguments as it is written, unless it formats every
 * message with {@link java.text.MessageFormat}, which rejects a placeholder that is a name. Such a
 * message source is refused when the lookup is created, not when the first violation needs a
 * message.
 */
final class MessageSourceTemplates implements BiFunction<String, Locale, Optional<String>> {

  /** Two apostrophes, which {@link java.text.MessageFormat} writes as one. */
  private static final String PROBE = "''";

  private final MessageSource messageSource;

  /**
   * Creates the lookup.
   *
   * @throws IllegalStateException if the message source formats messages that have no arguments
   */
  MessageSourceTemplates(MessageSource messageSource) {
    var written =
        messageSource.getMessage(new DefaultMessageSourceResolvable(null, PROBE), Locale.ROOT);
    if (!PROBE.equals(written)) {
      throw new IllegalStateException(
          "the message source of the application formats every message with MessageFormat, as"
              + " spring.messages.always-use-message-format=true makes it, and MessageFormat"
              + " rejects the {name} placeholders of Muster's messages; turn that off, or define a"
              + " bean of type MessageResolver to resolve the messages of violations");
    }
    this.messageSource = messageSource;
  }

  @Override
  public Optional<String> apply(String code, Locale locale) {
    return Optional.ofNullable(messageSource.getMessage(code, null, null, locale));
  }
}
