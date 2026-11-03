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
import me.yakar.muster.rule.BusinessRule;
import me.yakar.muster.rule.Rule;
import me.yakar.muster.rule.RuleContext;
import me.yakar.muster.rule.RuleResult;

// tag::whole[]
/** A call creates a few vehicles at most. */
@Rule(id = "vehicle.batch-limit")
final class BatchLimit implements BusinessRule<List<CreateVehicle>> {

  private final int limit;

  BatchLimit(int limit) {
    this.limit = limit;
  }

  @Override
  public RuleResult check(List<CreateVehicle> input, RuleContext ctx) {
    return input.size() <= limit
        ? RuleResult.passed()
        : RuleResult.failed(ctx.violation().clientArg("limit", limit).build());
  }
}
// end::whole[]
