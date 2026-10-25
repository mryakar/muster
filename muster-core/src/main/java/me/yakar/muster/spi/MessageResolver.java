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
package me.yakar.muster.spi;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.BiFunction;
import java.util.function.Function;
import me.yakar.muster.internal.message.ResourceBundleTemplates;
import me.yakar.muster.internal.message.TemplateMessageResolver;
import me.yakar.muster.rule.Violation;

/**
 * Turns the code and client arguments of a violation into a message in the locale of the call. The
 * edge of the application, such as an exception handler, resolves messages; internal arguments
 * never reach a resolver.
 *
 * <p>The resolvers that {@link #ofTemplates} and {@link #ofResourceBundles} create fill a template
 * in which {@code {name}} stands for the client argument of that name:
 *
 * <ul>
 *   <li>The value is written with {@link String#valueOf(Object)}; a rule formats numbers and dates
 *       itself.
 *   <li>An unknown name, braces included, and {@code {}} stay as they are.
 *   <li><code>&#123;&#123;</code> and <code>&#125;&#125;</code> write one brace. Other braces and
 *       apostrophes need no escaping, unlike in {@link java.text.MessageFormat}.
 * </ul>
 *
 * <p>Filling never fails. A resolver must be thread-safe; those that Muster creates are.
 */
@FunctionalInterface
public interface MessageResolver {

  /**
   * Returns the message for a code.
   *
   * @param code the code of a violation
   * @param clientArgs the client arguments of the violation
   * @param locale the locale of the call
   * @return the message, or the code if there is none
   */
  String resolve(String code, Map<String, ?> clientArgs, Locale locale);

  /**
   * Returns the message for a violation, from its code and its client arguments.
   *
   * @param violation the violation
   * @param locale the locale of the call
   * @return the message, or the code if there is none
   */
  default String resolve(Violation violation, Locale locale) {
    return resolve(violation.code(), violation.clientArgs(), locale);
  }

  /**
   * Creates a resolver that looks up the template for a code, for example in a database table or a
   * framework's message source, and fills it with the client arguments. Without a template, the
   * message is the code.
   *
   * @param templates looks up the template for a code in a locale, as written, with its {@code
   *     {name}} placeholders; it must be thread-safe
   * @return the resolver
   */
  static MessageResolver ofTemplates(BiFunction<String, Locale, Optional<String>> templates) {
    return new TemplateMessageResolver(templates);
  }

  /**
   * Creates a resolver that reads templates from a family of resource bundles, with the code as the
   * key. Without a template, the message is the code.
   *
   * {@snippet :
   * var messages =
   *     MessageResolver.ofResourceBundles(
   *         locale -> ResourceBundle.getBundle("com.example.messages", locale));
   * String message = messages.resolve(violation, locale);
   * }
   *
   * <p>Load the bundles in your own module, as above, so that they are found on the module path.
   * The bundle for a locale is its own or a more general one, otherwise the base bundle: unlike
   * {@code getBundle}, the resolver never uses the bundle of the JVM's default locale. The family
   * therefore needs a base bundle, and its values must be strings.
   *
   * @param bundles returns the bundle for a locale, as {@link ResourceBundle#getBundle(String,
   *     Locale)} loads it; called on every resolve, and {@code getBundle} caches the bundles
   * @return the resolver
   * @throws IllegalArgumentException if the family has no base bundle, or the function returns a
   *     bundle that {@code getBundle} did not load
   */
  static MessageResolver ofResourceBundles(Function<Locale, ResourceBundle> bundles) {
    return new TemplateMessageResolver(new ResourceBundleTemplates(bundles));
  }
}
