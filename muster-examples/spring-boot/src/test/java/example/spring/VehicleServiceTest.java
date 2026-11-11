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
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

// tag::values-test[]
/** The rules load a vehicle once, for each other and for the service method. */
@SpringBootTest
@Transactional
class VehicleServiceTest {

  @Autowired private VehicleService service;
  @MockitoSpyBean private VehicleRepository vehicles;

  @Test
  @WithMockUser("ece")
  void renamesTheVehicleThatItsRulesLoaded() {
    var vehicle = service.create(new CreateVehicle("Atlas", "34 ABC 123"));
    clearInvocations(vehicles);

    var renamed = service.rename(new RenameVehicle(vehicle.id(), "Atlas II"));

    assertEquals(new Vehicle(vehicle.id(), "Atlas II", "34 ABC 123", "ece"), renamed);
    verify(vehicles).find(vehicle.id()); // once: VehicleExists, for the other rule and the service
  }
}
// end::values-test[]
