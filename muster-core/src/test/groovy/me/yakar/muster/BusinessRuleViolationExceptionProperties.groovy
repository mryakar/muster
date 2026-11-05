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
package me.yakar.muster

import static me.yakar.muster.rule.ViolationProperties.violation

import me.yakar.muster.rule.ViolationProperties
import net.jqwik.api.Arbitrary
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide

/**
 * The exception survives Java serialization with its violations in order, each without the internal
 * arguments, which serialization drops.
 */
class BusinessRuleViolationExceptionProperties {

  @Provide
  Arbitrary<Map<String, Object>> violations() {
    ViolationProperties.violationParts()
  }

  @Property
  void 'the exception comes back with the same violations, in order'(
      @ForAll('violations') Map<String, Object> first, @ForAll('violations') Map<String, Object> second) {
    def exception = new BusinessRuleViolationException([violation(first), violation(second)])
    def copy = Serialization.roundTrip(exception)
    assert copy.violations() == [violation(first, false), violation(second, false)]
    assert copy.message == exception.message
  }
}
