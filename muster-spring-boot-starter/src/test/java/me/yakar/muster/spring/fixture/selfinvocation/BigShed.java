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
package me.yakar.muster.spring.fixture.selfinvocation;

import org.springframework.stereotype.Service;

/** Overrides and calls itself: with its own input, which the proxy checked, or with another. */
@Service
public class BigShed extends Shed {

  @Override
  public void save(Vehicle vehicle) {
    super.save(vehicle);
  }

  public void saveTwin(Vehicle vehicle) {
    save(new Vehicle(vehicle.name() + " II"));
  }

  public void saveTrimmed(Vehicle vehicle) {
    if (vehicle.name().endsWith(" ")) {
      saveTrimmed(new Vehicle(vehicle.name().strip()));
    }
  }

  public void saveAgain(Vehicle vehicle) {
    if (vehicle.name().isEmpty()) {
      saveAgain(vehicle);
    }
  }
}
