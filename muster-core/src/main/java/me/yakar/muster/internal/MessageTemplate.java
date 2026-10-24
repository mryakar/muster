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
package me.yakar.muster.internal;

import java.util.Map;

/**
 * Fills a message template: {@code {name}} stands for the argument of that name, and <code>
 * &#123;&#123;</code> and <code>&#125;&#125;</code> write a single brace. A name that is not an
 * argument stays as it is, and so does any other brace.
 */
final class MessageTemplate {

  private MessageTemplate() {}

  static String fill(String template, Map<String, ?> args) {
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
        i = placeholder(template, i, args, text);
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
      String template, int open, Map<String, ?> args, StringBuilder text) {
    var close = open + 1;
    while (close < template.length() && !isBrace(template.charAt(close))) {
      close++;
    }
    if (close == template.length() || template.charAt(close) == '{') {
      // No closing brace, or a brace that may start a placeholder of its own comes first.
      text.append('{');
      return open + 1;
    }
    var value = args.get(template.substring(open + 1, close));
    if (value == null) {
      text.append(template, open, close + 1);
    } else {
      text.append(value);
    }
    return close + 1;
  }

  private static boolean isBrace(char c) {
    return c == '{' || c == '}';
  }
}
