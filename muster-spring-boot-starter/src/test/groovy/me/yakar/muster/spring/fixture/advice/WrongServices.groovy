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
package me.yakar.muster.spring.fixture.advice

import me.yakar.muster.CheckMode
import me.yakar.muster.spring.Rules
import org.springframework.stereotype.Service

/** A service method that takes a rule target among other parameters. */
@Service
class TwoParameters {
  void rename(Long id, SaveVehicle vehicle) {}
}

/** Rules on a method with two parameters. */
class TwoParametersWithRules {
  @Rules(VehicleExists)
  void rename(Long id, SaveVehicle vehicle) {}
}

/** Rules on a method without a parameter. */
class NoParameter {
  @Rules(VehicleExists)
  void deleteAll() {}
}

/** Rules on a method a proxy cannot call. */
class NotPublic {
  @Rules(VehicleExists)
  protected void delete(Long id) {}
}

/** Rules on a final method, which a class-based proxy cannot override. */
class FinalMethod {
  @Rules(VehicleExists)
  final void delete(Long id) {}
}

/** Rules on a static method. */
class StaticMethod {
  @Rules(VehicleExists)
  static void delete(Long id) {}
}

/** Two modes at once. */
class TwoModes {
  @Rules(mode = [
    CheckMode.FAIL_FAST,
    CheckMode.COLLECT_ALL
  ])
  void save(SaveVehicle vehicle) {}
}

/** A rule that is not registered. */
class UnregisteredRule {
  @Rules(me.yakar.muster.spring.fixture.other.OtherRule)
  void save(SaveVehicle vehicle) {}
}
