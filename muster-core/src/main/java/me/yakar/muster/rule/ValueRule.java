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
package me.yakar.muster.rule;

/**
 * A business rule that leaves a value when it passes, such as the entity it loaded, for the rules
 * that require it, so that they do not load it again.
 *
 * <p>The rule passes with its value, as {@link RuleResult#passed(Object)} gives it, and a rule that
 * requires it, directly or through other rules, reads it with {@link RuleContext#valueOf(Class)}.
 * The value type {@code V} is read from the class declaration, as the input type is:
 *
 * {@snippet :
 * @Rule(id = "vehicle.exists")
 * @RequiresRule({})
 * final class VehicleExists implements ValueRule<ExistingVehicle, Vehicle> {
 *
 *   @Override
 *   public RuleResult check(ExistingVehicle input, RuleContext ctx) {
 *     return vehicles
 *         .find(input.vehicleId())
 *         .map(RuleResult::passed)
 *         .orElseGet(() -> RuleResult.failed(ctx.violation().build()));
 *   }
 * }
 *
 * @Rule(id = "vehicle.owned-by-caller")
 * @RequiresRule(VehicleExists.class)
 * final class VehicleOwnedByCaller implements BusinessRule<ExistingVehicle> {
 *
 *   @Override
 *   public RuleResult check(ExistingVehicle input, RuleContext ctx) {
 *     Vehicle vehicle = ctx.valueOf(VehicleExists.class); // no second read
 *     // ...
 *   }
 * }
 * }
 *
 * <p>A value lives for one check: Muster keeps no value from one call to the next. In a check of a
 * collection, a rule of each element leaves a value for each element it passes. One that overrides
 * {@link #checkAll} returns the result of each element with {@link
 * RuleResult#ofElements(java.util.List)}.
 *
 * @param <T> the type of input the rule checks
 * @param <V> the type of value the rule leaves
 */
public interface ValueRule<T, V> extends BusinessRule<T> {}
