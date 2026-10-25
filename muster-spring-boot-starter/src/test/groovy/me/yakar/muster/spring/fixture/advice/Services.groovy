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

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.util.concurrent.CopyOnWriteArrayList
import me.yakar.muster.CheckMode
import me.yakar.muster.spring.Rules
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service

/** {@link Rules} as a meta-annotation. */
@Retention(RetentionPolicy.RUNTIME)
@Rules(VehicleExists)
@interface CheckedDelete {}

/** A service: its methods that take a rule target check the rules bound by type. */
@Service
class VehicleService {
  /** Threads may call the service at once. */
  final List<Object> calls = new CopyOnWriteArrayList<>()

  void save(SaveVehicle vehicle) {
    calls << vehicle
  }

  void update(VehicleFields vehicle) {
    calls << vehicle
  }

  @Rules(VehicleExists)
  void delete(Long id) {
    calls << id
  }

  @Rules(VehicleExists)
  void deletePrimitive(long id) {
    calls << id
  }

  @CheckedDelete
  void deleteMeta(Long id) {
    calls << id
  }

  @Rules(mode = CheckMode.COLLECT_ALL)
  void saveAll(SaveVehicle vehicle) {
    calls << vehicle
  }

  @Rules(exclude = NameShort)
  void saveLong(SaveVehicle vehicle) {
    calls << vehicle
  }

  String describe(String text) {
    text
  }

  @Rules(VehicleExists)
  String find(Long id) {
    "vehicle $id"
  }
}

/** Not a service: only its methods with rules check them. */
class VehicleComponent {
  final List<Object> calls = []

  void save(SaveVehicle vehicle) {
    calls << vehicle
  }

  @Rules
  void checked(SaveVehicle vehicle) {
    calls << vehicle
  }
}

/** Declares rules on an interface method. */
interface VehicleOperations {
  @Rules(VehicleExists)
  void delete(Long id)
}

/** Implements the rules of its interface. */
class OperationsService implements VehicleOperations {
  final List<Object> calls = []

  void delete(Long id) {
    calls << id
  }
}

/** A service created on first use, after the application has started. */
@Service
@Lazy
class LazyVehicleService {
  void save(SaveVehicle vehicle) {}
}
