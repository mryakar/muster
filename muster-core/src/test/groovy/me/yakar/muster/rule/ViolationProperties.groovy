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

import me.yakar.muster.Serialization
import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.Combinators
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide

/**
 * Violations survive Java serialization: equal to the original but without the internal arguments,
 * which serialization drops.
 */
class ViolationProperties {

  @Provide
  Arbitrary<Map<String, Object>> violations() {
    violationParts()
  }

  static Arbitrary<Map<String, Object>> violationParts() {
    def names = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(6)
    def values = Arbitraries.oneOf(
        Arbitraries.strings().ofMaxLength(10), Arbitraries.integers(), Arbitraries.longs())
    def args = Arbitraries.maps(names, values).ofMaxSize(3)
    Combinators.combine(
        Arbitraries.strings().withCharRange('a' as char, 'z' as char).ofMinLength(1).ofMaxLength(8),
        args,
        args,
        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(6).injectNull(0.3),
        Arbitraries.of(Category).injectNull(0.3))
        .as { code, client, internal, target, category ->
          [code: code, client: client, internal: internal, target: target, category: category]
        }
  }

  static Violation violation(Map<String, Object> parts, boolean withInternal = true) {
    def builder = RuleContext.forRule('vehicle.name-unique').violation(parts.code)
    parts.client.each { name, value -> builder.clientArg(name, value) }
    if (withInternal) {
      // A name is used once across both kinds of arguments.
      parts.internal.findAll { name, value -> !parts.client.containsKey(name) }
      .each { name, value -> builder.internalArg(name, value) }
    }
    if (parts.target != null) {
      builder.target(parts.target)
    }
    if (parts.category != null) {
      builder.category(parts.category)
    }
    builder.build()
  }

  @Property
  void 'a violation comes back equal, without its internal arguments'(
      @ForAll('violations') Map<String, Object> parts) {
    def copy = Serialization.roundTrip(violation(parts))
    assert copy == violation(parts, false)
    assert copy.internalArgs().isEmpty()
    // In the order they were added, which equality of maps does not look at.
    assert copy.clientArgs().entrySet() as List == violation(parts).clientArgs().entrySet() as List
  }
}
