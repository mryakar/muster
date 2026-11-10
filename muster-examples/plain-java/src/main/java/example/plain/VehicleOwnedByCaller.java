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

import java.security.Principal;
import me.yakar.muster.rule.BusinessRule;
import me.yakar.muster.rule.Category;
import me.yakar.muster.rule.RequiresRule;
import me.yakar.muster.rule.Rule;
import me.yakar.muster.rule.RuleContext;
import me.yakar.muster.rule.RuleResult;

// tag::requires[]
/** Only the owner of a vehicle can change it. */
@Rule(id = "vehicle.owned-by-caller")
@RequiresRule(VehicleExists.class)
final class VehicleOwnedByCaller implements BusinessRule<ExistingVehicle> {

  @Override
  public RuleResult check(ExistingVehicle input, RuleContext ctx) {
    // tag::caller[]
    var owner = ctx.valueOf(VehicleExists.class).owner(); // the vehicle VehicleExists loaded
    var caller = ctx.caller().map(Principal::getName);
    if (caller.filter(owner::equals).isPresent()) {
      return RuleResult.passed();
    }
    return RuleResult.failed(
        ctx.violation()
            .internalArg("owner", owner) // for logs; the caller learns nothing of the owner
            .category(Category.FORBIDDEN)
            .build());
    // end::caller[]
  }
}
// end::requires[]
