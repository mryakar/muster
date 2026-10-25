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
 * Turns the code and the client arguments of a violation into a message in the caller's locale.
 *
 * <p>Muster does not resolve messages itself: a violation carries a code and arguments, and the
 * edge of the application, such as an exception handler, resolves them for the response. A resolver
 * sees the client arguments only; internal arguments never reach a message.
 *
 * <p>The resolvers Muster creates, {@link #ofTemplates} and {@link #ofResourceBundles}, fill a
 * template in which {@code {name}} stands for the client argument of that name:
 *
 * <ul>
 *   <li>The value is written with {@link String#valueOf(Object)}. To format a number or a date, the
 *       rule formats the value before it adds the argument.
 *   <li>A name that is not a client argument stays as it is, braces included, and so does {@code
 *       {}}.
 *   <li><code>&#123;&#123;</code> and <code>&#125;&#125;</code> write a single brace, so {@code
 *       {{name}}} writes {@code {name}}. Any other brace is written as it is.
 *   <li>Apostrophes and other characters need no escaping, unlike in {@link
 *       java.text.MessageFormat}.
 * </ul>
 *
 * <p>A malformed template never makes filling fail. The resolvers Muster creates are safe to use
 * from many threads at once; a resolver of your own must be too.
 */
@FunctionalInterface
public interface MessageResolver {

  /**
   * Returns the message for a code.
   *
   * @param code the code of a violation
   * @param clientArgs the client arguments of the violation
   * @param locale the locale of the caller
   * @return the message, or the code if there is no message for it; never {@code null}
   */
  String resolve(String code, Map<String, ?> clientArgs, Locale locale);

  /**
   * Returns the message for a violation, from its code and its client arguments.
   *
   * @param violation the violation
   * @param locale the locale of the caller
   * @return the message, or the code if there is no message for it; never {@code null}
   */
  default String resolve(Violation violation, Locale locale) {
    return resolve(violation.code(), violation.clientArgs(), locale);
  }

  /**
   * Creates a resolver that looks up the template for a code and fills it with the client
   * arguments. If there is no template, the message is the code.
   *
   * <p>Use it to resolve messages from a source of your own, such as a database table or the
   * message source of a framework. The lookup must be safe to call from many threads at once.
   *
   * @param templates looks up the template for a code in a locale, as it is written, with its
   *     {@code {name}} placeholders
   * @return the resolver
   */
  static MessageResolver ofTemplates(BiFunction<String, Locale, Optional<String>> templates) {
    return new TemplateMessageResolver(templates);
  }

  /**
   * Creates a resolver that reads templates from a family of resource bundles, with the code as the
   * key, and fills them with the client arguments. If the bundle has no template for the code, the
   * message is the code.
   *
   * <p>Load the bundles in your own module, so that they are found on the module path as well as on
   * the class path:
   *
   * {@snippet :
   * var messages =
   *     MessageResolver.ofResourceBundles(
   *         locale -> ResourceBundle.getBundle("com.example.messages", locale));
   * String message = messages.resolve(violation, locale);
   * }
   *
   * <p>The bundle for a locale is the bundle of that locale or of a more general one, such as
   * {@code messages_tr.properties} for {@code tr-TR}, and otherwise the base bundle, {@code
   * messages.properties}. Unlike {@link ResourceBundle#getBundle(String, Locale)}, the resolver
   * never uses the bundle of the default locale of the JVM: a server whose default locale is
   * Turkish answers a French client from the base bundle, not in Turkish. The family therefore
   * needs a base bundle. Its values must be strings: {@link ResourceBundle#getString(String)}
   * throws a {@link ClassCastException} for a value of another type.
   *
   * @param bundles returns the bundle for a locale, as loaded by {@link
   *     ResourceBundle#getBundle(String, Locale)}, such as {@code locale ->
   *     ResourceBundle.getBundle("com.example.messages", locale)}; it is called on every resolve,
   *     and {@code getBundle} keeps the bundles it loads
   * @return the resolver
   * @throws IllegalArgumentException if the family has no base bundle, or the function returns a
   *     bundle that {@code getBundle} did not load, which does not know its locale
   */
  static MessageResolver ofResourceBundles(Function<Locale, ResourceBundle> bundles) {
    return new TemplateMessageResolver(new ResourceBundleTemplates(bundles));
  }
}
