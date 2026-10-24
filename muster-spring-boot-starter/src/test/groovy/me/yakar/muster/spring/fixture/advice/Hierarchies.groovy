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

import me.yakar.muster.rule.RuleTarget
import me.yakar.muster.spring.Rules
import org.springframework.stereotype.Service

/** A generic interface, whose implementation gets a bridge method. */
interface GenericOperations<T> {
  void delete(T id)
}

/** Rules on the implementing method of a generic interface. */
class GenericOperationsImpl implements GenericOperations<Long> {
  final List<Object> calls = []

  @Rules(VehicleExists)
  void delete(Long id) {
    calls << id
  }
}

/** An interface without rules, implemented by a method with rules. */
interface PlainOperations {
  void delete(Long id)
}

/** Rules on the implementing method only. */
class PlainOperationsImpl implements PlainOperations {
  final List<Object> calls = []

  @Rules(VehicleExists)
  void delete(Long id) {
    calls << id
  }
}

/** A generic interface for rule targets. */
interface TargetOperations<T extends RuleTarget> {
  void save(T target)
}

/** A service that implements a generic interface for rule targets. */
@Service
class TargetService implements TargetOperations<SaveVehicle> {
  final List<Object> calls = []

  void save(SaveVehicle vehicle) {
    calls << vehicle
  }
}

/** A generic base class of services. */
abstract class BaseSaver<T> {
  abstract void save(T target)
}

/** A service that overrides a generic method of its base class. */
@Service
class SubSaver extends BaseSaver<SaveVehicle> {
  final List<Object> calls = []

  void save(SaveVehicle vehicle) {
    calls << vehicle
  }
}

/** A base class with rules on a method that a subclass overrides with a narrower return type. */
class BaseFinder {
  @Rules(VehicleExists)
  Object find(Long id) {
    "base "
  }
}

/** Overrides a method with rules, with a narrower return type. */
class SubFinder extends BaseFinder {
  String find(Long id) {
    "sub "
  }
}

/** A base class of a service, which is no service itself. */
class BaseVehicleSaver {
  void save(SaveVehicle vehicle) {}
}

/** A service that inherits its method from its base class. */
@Service
class InheritingSaver extends BaseVehicleSaver {}

/** A service whose base class is a service: not a service itself. */
class NotAServiceItself extends VehicleService {}

/** A static method that takes a rule target on a service. */
@Service
class StaticSaver {
  static final List<Object> calls = []

  static void save(SaveVehicle vehicle) {
    calls << vehicle
  }
}

/** Calls as Java code does, through the declared type, so that bridges and interface methods are used. */
@groovy.transform.CompileStatic
class StaticCalls {
  static void deleteGeneric(GenericOperations<Long> operations, Long id) {
    operations.delete(id)
  }

  static void deletePlain(PlainOperations operations, Long id) {
    operations.delete(id)
  }

  static void saveTarget(TargetOperations<SaveVehicle> operations, SaveVehicle vehicle) {
    operations.save(vehicle)
  }

  static void saveBase(BaseSaver<SaveVehicle> saver, SaveVehicle vehicle) {
    saver.save(vehicle)
  }

  static Object findBase(BaseFinder finder, Long id) {
    finder.find(id)
  }

  static void deleteOperations(VehicleOperations operations, Long id) {
    operations.delete(id)
  }

  static void saveService(VehicleService service, SaveVehicle vehicle) {
    service.save(vehicle)
  }
}
