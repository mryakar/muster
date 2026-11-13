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
import java.util.ResourceBundle;
import java.util.function.BiFunction;
import java.util.function.Function;
import me.yakar.muster.spi.MessageResolver;

/**
 * Resolves a message by looking up its template and filling it with the client arguments, whose
 * values are formatted in the locale the lookup gives.
 *
 * <p>It keeps no state of its own besides the lookup.
 */
public final class TemplateMessageResolver implements MessageResolver {

  private static final System.Logger LOGGER = System.getLogger("me.yakar.muster");

  private final TemplateLookup templates;

  private TemplateMessageResolver(TemplateLookup templates) {
    this.templates = templates;
  }

  /**
   * Creates a resolver whose templates come from a lookup that does not tell their locale, so that
   * their values are formatted in the locale of the call.
   *
   * @param templates looks up the template for a code in a locale
   * @return the resolver
   */
  public static TemplateMessageResolver ofTemplates(
      BiFunction<String, Locale, Optional<String>> templates) {
    requireNonNull(templates, "templates");
    return new TemplateMessageResolver(
        (code, locale) ->
            requireNonNull(
                    templates.apply(code, locale),
                    () ->
                        "the template lookup returned null for '"
                            + code
                            + "'; return Optional.empty()")
                .map(text -> new LocalizedTemplate(text, locale)));
  }

  /**
   * Creates a resolver whose templates come from a family of resource bundles, and checks that the
   * family has a base bundle.
   *
   * @param bundles returns the bundle for a locale
   * @param baseLocale the locale of the base bundle, in which its values are formatted
   * @return the resolver
   * @throws IllegalArgumentException if the family has no base bundle
   */
  public static TemplateMessageResolver ofResourceBundles(
      Function<Locale, ResourceBundle> bundles, Locale baseLocale) {
    return new TemplateMessageResolver(new ResourceBundleTemplates(bundles, baseLocale));
  }

  @Override
  public String resolve(String code, Map<String, ?> clientArgs, Locale locale) {
    requireNonNull(code, "code");
    requireNonNull(clientArgs, "clientArgs");
    requireNonNull(locale, "locale");
    var template = templates.find(code, locale);
    if (template.isEmpty()) {
      LOGGER.log(
          Level.DEBUG,
          () -> "No message for '" + code + "' in locale '" + locale + "'; using the code");
      return code;
    }
    return MessageTemplate.fill(template.get().text(), clientArgs, template.get().locale());
  }
}
