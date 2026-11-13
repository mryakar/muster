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

import java.lang.System.Logger.Level;
import java.util.Locale;
import java.util.Map;

/**
 * Fills a message template: {@code {name}} stands for the argument of that name, {@code
 * {name,type}} and {@code {name,type,style}} for its value formatted as {@link ValueFormat} reads
 * the type and the style, and <code>&#123;&#123;</code> and <code>&#125;&#125;</code> write a
 * single brace.
 *
 * <p>A name that is not an argument stays as it is, and so do a format that is not one, {@code {}}
 * and any other brace. A placeholder that is the whole name of an argument, commas included, stands
 * for that argument, since names could contain commas before placeholders had formats. A value that
 * its format cannot format is written as it is. Names are taken as they are; those of a violation's
 * arguments contain no whitespace and no brace.
 */
final class MessageTemplate {

  private static final System.Logger LOGGER = System.getLogger("me.yakar.muster");

  private MessageTemplate() {}

  static String fill(String template, Map<String, ?> args, Locale locale) {
    var length = template.length();
    var text = new StringBuilder(length + 16);
    var i = 0;
    while (i < length) {
      var c = template.charAt(i);
      var next = i + 1 < length ? template.charAt(i + 1) : '\0';
      if (isBrace(c) && next == c) {
        text.append(c);
        i += 2;
      } else if (c == '{') {
        i = placeholder(template, i, args, locale, text);
      } else {
        text.append(c);
        i++;
      }
    }
    return text.toString();
  }

  /**
   * Writes the placeholder that starts at {@code open}, or only its brace if no argument name
   * follows, and returns where the text goes on.
   */
  private static int placeholder(
      String template, int open, Map<String, ?> args, Locale locale, StringBuilder text) {
    var close = open + 1;
    while (close < template.length() && !isBrace(template.charAt(close))) {
      close++;
    }
    if (close == template.length() || template.charAt(close) == '{') {
      // No closing brace, or a brace that may start a placeholder of its own comes first.
      text.append('{');
      return open + 1;
    }
    var filled = close > open + 1 && fill(template.substring(open + 1, close), args, locale, text);
    if (!filled) {
      text.append(template, open, close + 1);
    }
    return close + 1;
  }

  /**
   * Writes the value that a placeholder stands for, and tells whether it stands for one. An empty
   * name is no placeholder, even if a caller passes an argument named so.
   */
  private static boolean fill(
      String placeholder, Map<String, ?> args, Locale locale, StringBuilder text) {
    var value = args.get(placeholder);
    if (value != null) {
      text.append(value);
      return true;
    }
    var comma = placeholder.indexOf(',');
    if (comma < 1) {
      return false;
    }
    var format = ValueFormat.parse(placeholder.substring(comma + 1));
    value = args.get(placeholder.substring(0, comma));
    if (format == null || value == null) {
      return false;
    }
    var formatted = format.format(value, locale);
    if (formatted == null) {
      var unformatted = value;
      LOGGER.log(
          Level.DEBUG,
          () ->
              "Cannot format "
                  + unformatted.getClass().getName()
                  + " as {"
                  + placeholder
                  + "} in locale '"
                  + locale
                  + "'; writing it as it is");
      text.append(value);
    } else {
      text.append(formatted);
    }
    return true;
  }

  private static boolean isBrace(char c) {
    return c == '{' || c == '}';
  }
}
