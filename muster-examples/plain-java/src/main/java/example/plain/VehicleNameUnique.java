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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import me.yakar.muster.rule.BusinessRule;
import me.yakar.muster.rule.Category;
import me.yakar.muster.rule.Rule;
import me.yakar.muster.rule.RuleContext;
import me.yakar.muster.rule.RuleResult;
import me.yakar.muster.rule.Violation;

// tag::rule[]
/** A vehicle name must not be taken. */
@Rule(id = "vehicle.name-unique")
final class VehicleNameUnique implements BusinessRule<VehicleFields> {

  private final Vehicles vehicles;

  VehicleNameUnique(Vehicles vehicles) {
    this.vehicles = vehicles;
  }

  @Override
  public RuleResult check(VehicleFields input, RuleContext ctx) {
    return vehicles.isTaken(input.name())
        ? RuleResult.failed(taken(ctx, input.name()))
        : RuleResult.passed();
  }

  // end::rule[]

  // tag::check-all[]
  @Override
  public RuleResult checkAll(List<? extends VehicleFields> inputs, RuleContext ctx) {
    var taken = vehicles.takenAmong(inputs.stream().map(VehicleFields::name).toList()); // one query
    var seen = new HashSet<String>();
    var violations = new ArrayList<Violation>();
    for (var i = 0; i < inputs.size(); i++) {
      var name = inputs.get(i).name();
      if (taken.contains(name) || !seen.add(Vehicles.key(name))) {
        violations.add(taken(ctx.element(i), name));
      }
    }
    return violations.isEmpty() ? RuleResult.passed() : RuleResult.failed(violations);
  }

  // end::check-all[]

  // tag::violation[]
  private static Violation taken(RuleContext ctx, String name) {
    return ctx.violation()
        .clientArg("name", name)
        .target("name")
        .category(Category.CONFLICT)
        .build();
  }
  // end::violation[]
  // tag::rule[]
}
// end::rule[]
