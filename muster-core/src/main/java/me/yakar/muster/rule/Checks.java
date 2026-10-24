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
package me.yakar.muster.rule;

import static java.util.Objects.requireNonNull;

/** Argument checks shared by the core types. */
final class Checks {

  private Checks() {}

  static String requireNonBlank(String value, String name) {
    requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }

  /**
   * Requires a value that can serve as a key, such as a message key: not blank, and with no
   * whitespace, space, control or format character and no unpaired surrogate, which would break it
   * or hide in it unseen.
   */
  static String requireKey(String value, String name) {
    requireNonBlank(value, name);
    for (var i = 0; i < value.length(); ) {
      var c = value.codePointAt(i);
      if (Character.isWhitespace(c)
          || Character.isSpaceChar(c)
          || Character.isISOControl(c)
          || Character.getType(c) == Character.FORMAT
          || Character.getType(c) == Character.SURROGATE) {
        throw new IllegalArgumentException(
            String.format(
                "%s '%s' contains U+%04X; it cannot contain whitespace, spaces, control or format"
                    + " characters, or unpaired surrogates",
                name, value, c));
      }
      i += Character.charCount(c);
    }
    return value;
  }
}
