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
package me.yakar.muster.spring.fixture.lazy

import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import me.yakar.muster.spring.Rules
import org.springframework.stereotype.Service

/** An advised service that a rule depends on. */
@Service
class Plates {
  @Rules(PlateKnown)
  boolean known(String plate) {
    plate != 'unknown'
  }
}

/** A plate must be known. */
@Rule(id = 'lazy.plate-known')
class PlateKnown implements BusinessRule<String> {
  RuleResult check(String input, RuleContext ctx) {
    input == 'unknown' ? RuleResult.failed(ctx.violation().build()) : RuleResult.passed()
  }
}

/** A rule that depends on an advised service. */
@Rule(id = 'lazy.vehicle-has-plate')
class VehicleHasPlate implements BusinessRule<Long> {
  final Plates plates

  VehicleHasPlate(Plates plates) {
    this.plates = plates
  }

  RuleResult check(Long input, RuleContext ctx) {
    plates.known(input == 404L ? 'unknown' : 'TR-1') ? RuleResult.passed() : RuleResult.failed(ctx.violation().build())
  }
}

/** Uses the rule that depends on an advised service. */
@Service
class Fleet {
  @Rules(VehicleHasPlate)
  void retire(Long id) {}
}
