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

// tag::jdbc-test-imports[]
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import me.yakar.muster.rule.RuleContext;
import me.yakar.muster.rule.RuleResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;

// end::jdbc-test-imports[]

// tag::jdbc-test[]
/** The rule against the database, without the rest of the application. */
@JdbcTest
@Import(VehicleRepository.class)
class VehicleNameUniqueTest {

  @Autowired private VehicleRepository vehicles;

  @Test
  void rejectsATakenNameWhateverItsCase() {
    vehicles.add("Atlas", "34 ABC 123", "ece");
    var rule = new VehicleNameUnique(vehicles);

    var result =
        rule.check(
            new CreateVehicle("ATLAS", "34 ABC 124"), RuleContext.forRule(VehicleNameUnique.class));

    var violation = assertInstanceOf(RuleResult.Failed.class, result).violations().getFirst();
    assertEquals("vehicle.name-unique", violation.code());
  }
}
// end::jdbc-test[]
