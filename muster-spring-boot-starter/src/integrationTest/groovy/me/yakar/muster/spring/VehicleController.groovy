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
package me.yakar.muster.spring

import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** A service as an application writes it: its rules bind to the command by type. */
@Service
class VehicleService {
  void save(SaveVehicle command) {}
}

@RestController
class VehicleController {
  final VehicleService vehicles

  VehicleController(VehicleService vehicles) {
    this.vehicles = vehicles
  }

  @PostMapping('/vehicles')
  void save(@RequestBody SaveVehicle command) {
    vehicles.save(command)
  }
}
