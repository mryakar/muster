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
import java.util.Objects;
import me.yakar.muster.Binding;
import me.yakar.muster.CheckMode;
import me.yakar.muster.CollectionBinding;
import me.yakar.muster.Muster;
import me.yakar.muster.RuleChain;

/** Creates and renames vehicles, once the rules pass. */
final class VehicleService {

  private final Vehicles vehicles;
  private final RuleChain<CreateVehicle> create;
  private final Binding<RenameVehicle> rename;
  private final CollectionBinding<CreateVehicle> createAll;

  VehicleService(Muster muster, Vehicles vehicles) {
    this.vehicles = vehicles;
    // tag::chain[]
    create = muster.chain(CreateVehicle.class, VehiclePlateFormat.class, VehicleNameUnique.class);
    // end::chain[]
    // tag::binding[]
    rename =
        muster
            .binding(RenameVehicle.class)
            .name("VehicleService.rename")
            .mode(CheckMode.COLLECT_ALL)
            .build();
    // end::binding[]
    // tag::collection-binding[]
    createAll =
        muster
            .collectionBinding(CreateVehicle.class)
            .name("VehicleService.createAll")
            .rules(BatchLimit.class)
            .build();
    // end::collection-binding[]
  }

  // tag::check-first[]
  Vehicle create(CreateVehicle command) {
    // The first step inside the transaction of the operation, once there is one, so that the rules
    // read the data that the operation then changes.
    create.check(command);
    return vehicles.add(command.name(), command.plate(), owner());
  }

  // end::check-first[]
  void rename(RenameVehicle command) {
    rename.check(command);
    vehicles.rename(command.vehicleId(), command.name());
  }

  void createAll(List<CreateVehicle> commands) {
    createAll.check(commands);
    var owner = owner();
    commands.forEach(command -> vehicles.add(command.name(), command.plate(), owner));
  }

  /** Returns the caller, who owns the vehicles it creates: an anonymous call creates none. */
  private static String owner() {
    return Objects.requireNonNull(CurrentCall.caller(), "a vehicle needs an owner").getName();
  }
}
