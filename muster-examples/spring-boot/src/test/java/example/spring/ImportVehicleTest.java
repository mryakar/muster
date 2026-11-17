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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import me.yakar.muster.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/** The violations of an imported vehicle name the field of its command. */
@SpringBootTest
@Transactional
class ImportVehicleTest {

  @Autowired private VehicleService service;

  // tag::import-test[]
  @Test
  @WithMockUser("ece")
  void namesTheFieldOfTheCommandInTheViolation() {
    service.create(new CreateVehicle("Atlas", "34 ABC 123"));

    var e =
        assertThrows(
            BusinessRuleViolationException.class,
            () -> service.importVehicle(new ImportVehicle("Atlas", "06 XYZ 42")));

    var violation = e.violations().getFirst();
    assertEquals("vehicle.name-unique", violation.ruleId());
    assertEquals(Optional.of("vehicleName"), violation.target()); // the rule reported "name"
  }
  // end::import-test[]
}
