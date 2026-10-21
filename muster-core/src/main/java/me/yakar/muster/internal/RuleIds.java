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

import java.util.regex.Pattern;

/** The format of rule ids, shared by rule registration and rule contexts. */
public final class RuleIds {

  /** Describes the format, for error messages. */
  public static final String FORMAT =
      "an id starts with a lowercase letter and continues with lowercase letters, digits, and"
          + " single dots or hyphens between them, as in 'vehicle.name-unique'";

  private static final Pattern ID = Pattern.compile("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*");

  private RuleIds() {}

  /**
   * Returns whether a string is a valid rule id.
   *
   * @param id the string
   * @return whether it is a valid rule id
   */
  public static boolean isValid(String id) {
    return ID.matcher(id).matches();
  }
}
