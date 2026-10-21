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
 * Marks the inputs that rules bind to by type. Every enabled rule whose input type accepts a rule
 * target checks it, without being listed anywhere.
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
 * Long} in the application. A rule whose input type is {@code Object} binds to every rule target.
 */
public interface RuleTarget {}
