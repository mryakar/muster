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

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import me.yakar.muster.rule.BusinessRule;
import me.yakar.muster.rule.Rule;
import me.yakar.muster.rule.RuleContext;
import me.yakar.muster.rule.RuleResult;

// tag::locale[]
/**
 * A licence plate must have the format of the country the caller works in, which the region of the
 * locale of the call tells. The rule does not apply where it knows no format.
 */
@Rule(id = "vehicle.plate-format")
final class VehiclePlateFormat implements BusinessRule<CreateVehicle> {

  private static final Map<String, Pattern> FORMATS =
      Map.of(
          "TR", Pattern.compile("\\d{2} [A-Z]{1,3} \\d{2,4}"),
          "DE", Pattern.compile("[A-ZÄÖÜ]{1,3}-[A-Z]{1,2} \\d{1,4}"));

  @Override
  public boolean appliesTo(RuleContext ctx) {
    return FORMATS.containsKey(ctx.locale().getCountry());
  }

  @Override
  public RuleResult check(CreateVehicle input, RuleContext ctx) {
    return FORMATS.get(ctx.locale().getCountry()).matcher(input.plate()).matches()
        ? RuleResult.passed()
        : RuleResult.failed(
            ctx.violation().clientArg("plate", input.plate()).target("plate").build());
  }

  // end::locale[]

  // tag::one-by-one[]
  /** Checks each element on its own, which costs nothing here, without the warning. */
  @Override
  public RuleResult checkAll(List<? extends CreateVehicle> inputs, RuleContext ctx) {
    return BusinessRule.super.checkAll(inputs, ctx);
  }
  // end::one-by-one[]
  // tag::locale[]
}
// end::locale[]
