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

import java.util.Optional;
import me.yakar.muster.rule.Category;
import me.yakar.muster.rule.RuleContext;
import me.yakar.muster.rule.RuleResult;
import org.junit.jupiter.api.Test;

class VehicleOwnedByCallerTest {

  // tag::test-caller[]
  private final Vehicles vehicles = new Vehicles();
  private final VehicleOwnedByCaller rule = new VehicleOwnedByCaller(vehicles);
  private final long atlas = vehicles.add("Atlas", "34 ABC 123", "ece").id();

  // end::test-caller[]

  @Test
  void passesTheOwner() {
    var result = rule.check(new RenameVehicle(atlas, "Atlas II"), as("ece"));

    assertEquals(RuleResult.passed(), result);
  }

  // tag::test-caller[]
  @Test
  void forbidsAnotherCaller() {
    var ctx = RuleContext.builder(VehicleOwnedByCaller.class).caller(new User("can")).build();

    var result = rule.check(new RenameVehicle(atlas, "Atlas II"), ctx);

    var violation = assertInstanceOf(RuleResult.Failed.class, result).violations().getFirst();
    assertEquals(Optional.of(Category.FORBIDDEN), violation.category());
  }

  // end::test-caller[]

  @Test
  void forbidsACallWithoutACaller() {
    var result =
        rule.check(
            new RenameVehicle(atlas, "Atlas II"), RuleContext.forRule(VehicleOwnedByCaller.class));

    assertInstanceOf(RuleResult.Failed.class, result);
  }

  private static RuleContext as(String name) {
    return RuleContext.builder(VehicleOwnedByCaller.class).caller(new User(name)).build();
  }
}
