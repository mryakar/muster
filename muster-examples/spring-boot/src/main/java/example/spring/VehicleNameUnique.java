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
package example.spring;

import java.util.Set;
import me.yakar.muster.rule.BusinessRule;
import me.yakar.muster.rule.Category;
import me.yakar.muster.rule.Rule;
import me.yakar.muster.rule.RuleContext;
import me.yakar.muster.rule.RuleResult;

/** A vehicle name must not be taken. A rule class without {@code @Component}. */
@Rule(id = "vehicle.name-unique")
public class VehicleNameUnique implements BusinessRule<CreateVehicle> {

  private static final Set<String> TAKEN = Set.of("Atlas");

  @Override
  public RuleResult check(CreateVehicle input, RuleContext ctx) {
    return TAKEN.contains(input.name())
        ? RuleResult.failed(
            ctx.violation("vehicle.name-taken")
                .clientArg("name", input.name())
                .target("name")
                .category(Category.CONFLICT)
                .build())
        : RuleResult.passed();
  }
}
