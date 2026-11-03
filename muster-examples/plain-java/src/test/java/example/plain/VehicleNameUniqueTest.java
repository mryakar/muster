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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.Optional;
import me.yakar.muster.rule.RuleContext;
import me.yakar.muster.rule.RuleResult;
import org.junit.jupiter.api.Test;

// tag::test[]
class VehicleNameUniqueTest {

  // tag::test-check-all[]
  private final Vehicles vehicles = new Vehicles();
  private final VehicleNameUnique rule = new VehicleNameUnique(vehicles);

  // end::test-check-all[]

  @Test
  void rejectsATakenName() {
    vehicles.add("Atlas", "34 ABC 123", "ece");

    var result =
        rule.check(
            new CreateVehicle("ATLAS", "34 ABC 124"), RuleContext.forRule(VehicleNameUnique.class));

    var violation = assertInstanceOf(RuleResult.Failed.class, result).violations().getFirst();
    assertEquals("vehicle.name-unique", violation.code());
    assertEquals(Optional.of("name"), violation.target());
  }

  // end::test[]

  @Test
  void passesAFreeName() {
    var result =
        rule.check(
            new CreateVehicle("Boreas", "34 BOR 10"), RuleContext.forRule(VehicleNameUnique.class));

    assertEquals(RuleResult.passed(), result);
  }

  // tag::test-check-all[]
  @Test
  void rejectsTheSecondOfTwoEqualNames() {
    // The inputs are the elements at positions 0 and 2 of the checked collection.
    var ctx = RuleContext.builder(VehicleNameUnique.class).positions(0, 2).build();

    var result =
        rule.checkAll(
            List.of(
                new CreateVehicle("Boreas", "34 BOR 10"), new CreateVehicle("boreas", "34 BOR 11")),
            ctx);

    var violation = assertInstanceOf(RuleResult.Failed.class, result).violations().getFirst();
    assertEquals(Optional.of("[2].name"), violation.target());
  }
  // end::test-check-all[]
  // tag::test[]
}
// end::test[]
