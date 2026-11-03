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
package me.yakar.muster.rule

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import net.jqwik.api.Tuple

/**
 * The keys and argument names that violations accept, against a definition written apart from the
 * code: not empty, and no separator, control or format character or unpaired surrogate; an argument
 * name has no brace either.
 */
class ChecksProperties {

  private static final String FORBIDDEN = /[\p{Z}\p{Cc}\p{Cf}\p{Cs}]/

  static boolean validKey(String value) {
    !value.isEmpty() && !(value =~ FORBIDDEN).find()
  }

  @Provide
  Arbitrary<String> values() {
    // Any char, unpaired surrogates included; code points beyond the first plane, as surrogate
    // pairs, which chance alone seldom makes; and format characters among them, which it never
    // does.
    def anyChar = Arbitraries.chars().all().map { String.valueOf(it) }
    def supplementary = Arbitraries.integers().between(0x10000, 0x10FFFF).map { codePoint(it) }
    def supplementaryFormat = Arbitraries.of(0x110BD, 0x1D173, 0x1D17A, 0xE0001, 0xE0020, 0xE007F)
        .map { codePoint(it) }
    Arbitraries.frequencyOf(Tuple.of(6, anyChar), Tuple.of(3, supplementary), Tuple.of(1, supplementaryFormat))
        .list().ofMaxSize(12).map { it.join() }
  }

  private static String codePoint(int value) {
    new String(Character.toChars(value))
  }

  @Property
  void 'a code is accepted exactly when it is a key, and kept as it is'(@ForAll('values') String code) {
    def ctx = RuleContext.forRule('vehicle.name-unique')
    if (validKey(code)) {
      assert ctx.violation(code).build().code() == code
    } else {
      try {
        ctx.violation(code)
        assert false: "accepted the code '$code'"
      } catch (IllegalArgumentException ignored) {
      }
    }
  }

  @Property
  void 'an argument name is accepted exactly when it is a key without braces'(@ForAll('values') String name) {
    def builder = RuleContext.forRule('vehicle.name-unique').violation()
    def valid = validKey(name) && !name.contains('{') && !name.contains('}')
    try {
      builder.clientArg(name, 'Atlas')
      assert valid: "accepted the argument name '$name'"
      assert builder.build().clientArgs().keySet() == [name] as Set
    } catch (IllegalArgumentException ignored) {
      assert !valid: "rejected the argument name '$name'"
    }
  }
}
