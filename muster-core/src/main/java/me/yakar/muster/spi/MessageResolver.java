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
import java.util.function.BiFunction;
import me.yakar.muster.internal.TemplateMessageResolver;
import me.yakar.muster.rule.Violation;

/**
 * Turns the code and the client arguments of a violation into a message in the caller's locale.
 *
 * <p>Muster does not resolve messages itself: a violation carries a code and arguments, and the
 * edge of the application, such as an exception handler, resolves them for the response. A resolver
 * sees the client arguments only; internal arguments never reach a message.
 *
 * <p>The resolvers Muster creates with {@link #ofTemplates} fill a template in which {@code {name}}
 * stands for the client argument of that name:
 *
 * <ul>
 *   <li>The value is written with {@link String#valueOf(Object)}. To format a number or a date, the
 *       rule formats the value before it adds the argument.
 *   <li>A name that is not a client argument stays as it is, braces included.
 *   <li><code>&#123;&#123;</code> and <code>&#125;&#125;</code> write a single brace, so {@code
 *       {{name}}} writes {@code {name}}. Any other brace is written as it is.
 *   <li>Apostrophes and other characters need no escaping, unlike in {@link
 *       java.text.MessageFormat}.
 * </ul>
 *
 * <p>Filling a template never fails. A resolver is safe to use from many threads at once.
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
   * @return the message, or the code if there is no message for it
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
}
