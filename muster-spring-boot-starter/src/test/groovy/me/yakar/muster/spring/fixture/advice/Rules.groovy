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
package me.yakar.muster.spring.fixture.advice

import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import me.yakar.muster.rule.RuleTarget

/** The fields that the vehicle rules check. */
interface VehicleFields extends RuleTarget {
  String name()
}

/** Saves a vehicle. */
record SaveVehicle(String name) implements VehicleFields {}

/** Renames a vehicle. */
record RenameVehicle(String name) implements VehicleFields {}

/** A name must not be taken: names that start with Atlas are. */
@Rule(id = 'advice.name-free')
class NameFree implements BusinessRule<VehicleFields> {
  RuleResult check(VehicleFields input, RuleContext ctx) {
    input.name().startsWith('Atlas') ? RuleResult.failed(ctx.violation().build()) : RuleResult.passed()
  }
}

/** A name must be short. */
@Rule(id = 'advice.name-short')
class NameShort implements BusinessRule<VehicleFields> {
  RuleResult check(VehicleFields input, RuleContext ctx) {
    input.name().length() <= 10 ? RuleResult.passed() : RuleResult.failed(ctx.violation().build())
  }
}

/** A vehicle must exist: 404 does not. */
@Rule(id = 'advice.vehicle-exists')
class VehicleExists implements BusinessRule<Long> {
  RuleResult check(Long input, RuleContext ctx) {
    input == 404L ? RuleResult.failed(ctx.violation().build()) : RuleResult.passed()
  }
}
