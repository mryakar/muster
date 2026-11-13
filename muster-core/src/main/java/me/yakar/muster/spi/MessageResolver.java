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
 *   <li>The value is written with {@link String#valueOf(Object)}.
 *   <li>A placeholder with a type formats the value, with the names of {@link
 *       java.text.MessageFormat} and {@code datetime} for a date and time:
 *       <ul>
 *         <li>{@code {amount,number}}, {@code {count,number,integer}} and {@code
 *             {rate,number,percent}} format a {@link Number}. A {@link java.math.BigDecimal} keeps
 *             its scale, so {@code 12.50} is written {@code 12.50}, and one with more than 1,000
 *             digits before or after its point is not formatted; other numbers get at most three
 *             fraction digits.
 *         <li>{@code {when,date}}, {@code {at,time}} and {@code {at,datetime}} format a {@code
 *             java.time} value in a style: {@code short}, {@code medium}, which is the default,
 *             {@code long} or {@code full}, as in {@code {when,date,short}}. A date takes a {@code
 *             LocalDate} or a date and time; a time takes a {@code LocalTime}, an {@code
 *             OffsetTime} or a date and time; the long and full styles of a time, or of a date and
 *             time, write the name of its zone, so they take a {@code ZonedDateTime}, not an
 *             offset. An {@code Instant} has no date until it is given a zone.
 *       </ul>
 *       The values are formatted in the locale of the template that was found; where its source
 *       does not tell that locale, as with {@link #ofTemplates}, in the locale of the call. A value
 *       that its format cannot format is written with {@code String.valueOf}, and logged at DEBUG.
 *       The edge can also format the values itself and resolve the message with {@link
 *       #resolve(String, Map, Locale)}.
 *   <li>An unknown name or format, braces included, and {@code {}} stay as they are. A placeholder
 *       that is the whole name of an argument, commas included, stands for that argument.
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
    return TemplateMessageResolver.ofTemplates(templates);
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
   * <p>The values of a template in the language of the call are formatted in the locale of the
   * call, region included. Those of a template from the base bundle are formatted in the root
   * locale, which belongs to no language; {@link #ofResourceBundles(Function, Locale)} names the
   * language of the base bundle instead, so that a call in that language gets its own region.
   *
   * @param bundles returns the bundle for a locale, as {@link ResourceBundle#getBundle(String,
   *     Locale)} loads it; called on every resolve, and {@code getBundle} caches the bundles
   * @return the resolver
   * @throws IllegalArgumentException if the family has no base bundle, or the function returns, for
   *     the root locale, a bundle that {@code getBundle} did not load; a resolve throws it too for
   *     a bundle of another locale that {@code getBundle} did not load
   */
  static MessageResolver ofResourceBundles(Function<Locale, ResourceBundle> bundles) {
    return ofResourceBundles(bundles, Locale.ROOT);
  }

  /**
   * Creates a resolver that reads templates from a family of resource bundles, as {@link
   * #ofResourceBundles(Function)} does, and formats the values of the templates of the base bundle
   * in the locale it is written in.
   *
   * {@snippet :
   * var messages =
   *     MessageResolver.ofResourceBundles(
   *         locale -> ResourceBundle.getBundle("com.example.messages", locale), Locale.ENGLISH);
   * }
   *
   * <p>A template that a bundle inherits from the base bundle, or that is the same as the base
   * bundle's, is a template of the base bundle. Its values are formatted in the locale of the call
   * if the call is in the language of the base bundle, as an {@code en-GB} call is for an English
   * base bundle, and in the locale of the base bundle otherwise.
   *
   * @param bundles returns the bundle for a locale, as {@link ResourceBundle#getBundle(String,
   *     Locale)} loads it; called on every resolve, and {@code getBundle} caches the bundles
   * @param baseLocale the locale the base bundle is written in, such as {@link Locale#ENGLISH}
   * @return the resolver
   * @throws IllegalArgumentException if the family has no base bundle, or the function returns, for
   *     the root locale, a bundle that {@code getBundle} did not load; a resolve throws it too for
   *     a bundle of another locale that {@code getBundle} did not load
   */
  static MessageResolver ofResourceBundles(
      Function<Locale, ResourceBundle> bundles, Locale baseLocale) {
    return TemplateMessageResolver.ofResourceBundles(bundles, baseLocale);
  }
}
