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
 * Marks the inputs that rules bind to by type. When a rule target is passed to {@link
 * me.yakar.muster.Muster#check}, every enabled rule whose input type accepts it checks it, without
 * being listed anywhere. A chain created with {@link me.yakar.muster.Muster#chain} runs only the
 * rules it lists, and the rules they require.
 *
 * {@snippet :
 * interface VehicleFields extends RuleTarget {
 *   String code();
 *
 *   String name();
 * }
 *
 * record CreateVehicle(String code, String name) implements VehicleFields {}
 *
 * // VehicleNameUnique implements BusinessRule<VehicleFields>, so it checks every CreateVehicle
 * muster.check(new CreateVehicle("TR-1", "Atlas"));
 * }
 *
 * <p>Only marked inputs bind by type; otherwise a rule for {@code Long} would check every {@code
 * Long} in the application. A rule whose input type is a wide supertype, such as {@code Object},
 * {@code Record} or {@code RuleTarget} itself, binds to every rule target of that type.
 */
public interface RuleTarget {}
