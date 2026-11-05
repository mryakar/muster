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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.security.Principal;
import java.util.List;
import java.util.Objects;
import me.yakar.muster.CheckMode;
import me.yakar.muster.spring.Rules;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

// tag::service[]
/** Creates and renames the vehicles of the fleet, once the rules pass. */
@Service
@Validated
public class VehicleService {

  private final VehicleRepository vehicles;

  // end::service[]
  private final ObjectProvider<VehicleService> self;

  // tag::self[]
  public VehicleService(VehicleRepository vehicles, ObjectProvider<VehicleService> self) {
    this.vehicles = vehicles;
    this.self = self; // the bean, through which a call checks the rules
  }

  // end::self[]
  // tag::service[]
  @Transactional
  public Vehicle create(@Valid CreateVehicle command) {
    return vehicles.add(command.name(), command.plate(), owner());
  }

  // end::service[]
  // tag::rename[]
  @Transactional
  @Rules(mode = CheckMode.COLLECT_ALL)
  public void rename(@Valid RenameVehicle command) {
    vehicles.rename(command.vehicleId(), command.name());
  }

  // end::rename[]
  // tag::create-all[]
  @Transactional
  @Rules(BatchLimit.class)
  public List<Vehicle> createAll(List<@NotNull @Valid CreateVehicle> commands) {
    var owner = owner();
    return commands.stream()
        .map(command -> vehicles.add(command.name(), command.plate(), owner))
        .toList();
  }

  // end::create-all[]
  // tag::name-after-plate[]
  /** Names a vehicle after its plate, as renaming it would, with the rules of a rename. */
  @Transactional
  public void nameAfterPlate(long vehicleId) {
    // A vehicle that does not exist keeps its id as the name, and VehicleExists answers it.
    var plate = vehicles.find(vehicleId).map(Vehicle::plate).orElse(Long.toString(vehicleId));
    self.getObject().rename(new RenameVehicle(vehicleId, plate)); // not this.rename(...)
  }

  // end::name-after-plate[]
  // tag::service[]
  /** Returns the signed-in user, who owns the vehicles they create. */
  private static String owner() {
    Principal caller = SecurityContextHolder.getContext().getAuthentication();
    return Objects.requireNonNull(caller, "a vehicle needs an owner").getName();
  }
}
// end::service[]
