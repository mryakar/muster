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
package example.plain;

import me.yakar.muster.rule.BusinessRule;
import me.yakar.muster.rule.Category;
import me.yakar.muster.rule.Rule;
import me.yakar.muster.rule.RuleContext;
import me.yakar.muster.rule.RuleResult;

// tag::exists[]
/** The vehicle a command is about must exist. */
@Rule(id = "vehicle.exists")
final class VehicleExists implements BusinessRule<ExistingVehicle> {

  private final Vehicles vehicles;

  VehicleExists(Vehicles vehicles) {
    this.vehicles = vehicles;
  }

  @Override
  public RuleResult check(ExistingVehicle input, RuleContext ctx) {
    return vehicles.find(input.vehicleId()).isPresent()
        ? RuleResult.passed()
        : RuleResult.failed(
            ctx.violation()
                .clientArg("id", input.vehicleId())
                .category(Category.NOT_FOUND)
                .build());
  }
}
// end::exists[]
