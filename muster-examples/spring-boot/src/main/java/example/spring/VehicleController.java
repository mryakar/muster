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

import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The vehicles of the fleet over HTTP. It only translates: the service checks the rules. */
@RestController
@RequestMapping("/vehicles")
public class VehicleController {

  private final VehicleService vehicles;

  public VehicleController(VehicleService vehicles) {
    this.vehicles = vehicles;
  }

  @PostMapping
  public Vehicle create(@RequestBody CreateVehicle command) {
    return vehicles.create(command);
  }

  @PostMapping("/batch")
  public List<Vehicle> createAll(@RequestBody List<CreateVehicle> commands) {
    return vehicles.createAll(commands);
  }

  @PutMapping("/{id}/name")
  public void rename(@PathVariable("id") long id, @RequestBody NewName body) {
    vehicles.rename(new RenameVehicle(id, body.name()));
  }

  @PostMapping("/{id}/name-after-plate")
  public void nameAfterPlate(@PathVariable("id") long id) {
    vehicles.nameAfterPlate(id);
  }

  /** The body of a rename. */
  public record NewName(String name) {}
}
