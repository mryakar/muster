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

import jakarta.validation.constraints.NotBlank;

// tag::command[]
/**
 * The command to import a vehicle from another fleet, whose records call the name {@code
 * vehicleName}. The rules of {@link VehicleFields} check it as a name.
 */
public record ImportVehicle(@NotBlank String vehicleName, @NotBlank String plate)
    implements VehicleFields {

  @Override
  public String name() {
    return vehicleName;
  }
}
// end::command[]
