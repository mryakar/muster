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
package me.yakar.muster.internal.message;

import static java.util.Objects.requireNonNull;

import java.lang.System.Logger.Level;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import me.yakar.muster.spi.MessageResolver;

/**
 * Resolves a message by looking up its template and filling it with the client arguments.
 *
 * <p>It keeps no state of its own besides the lookup.
 */
public final class TemplateMessageResolver implements MessageResolver {

  private static final System.Logger LOGGER = System.getLogger("me.yakar.muster");

  private final BiFunction<String, Locale, Optional<String>> templates;

  /**
   * Creates a resolver.
   *
   * @param templates looks up the template for a code in a locale
   */
  public TemplateMessageResolver(BiFunction<String, Locale, Optional<String>> templates) {
    this.templates = requireNonNull(templates, "templates");
  }

  @Override
  public String resolve(String code, Map<String, ?> clientArgs, Locale locale) {
    requireNonNull(code, "code");
    requireNonNull(clientArgs, "clientArgs");
    requireNonNull(locale, "locale");
    var template =
        requireNonNull(
            templates.apply(code, locale),
            () -> "the template lookup returned null for '" + code + "'; return Optional.empty()");
    if (template.isEmpty()) {
      LOGGER.log(
          Level.DEBUG,
          () -> "No message for '" + code + "' in locale '" + locale + "'; using the code");
      return code;
    }
    return MessageTemplate.fill(template.get(), clientArgs);
  }
}
