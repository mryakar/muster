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
package me.yakar.muster.spring.fixture.collection

import java.util.concurrent.CopyOnWriteArrayList
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import me.yakar.muster.rule.RuleTarget
import me.yakar.muster.spring.Rules
import org.springframework.stereotype.Service

/** The fields that the fleet rules check. */
interface FleetFields extends RuleTarget {
  String name()
}

/** Adds a vehicle to a fleet. */
record AddVehicle(String name) implements FleetFields {}

/** A name must not be taken: names that start with Atlas are. */
@Rule(id = 'fleet.name-free')
class NameFree implements BusinessRule<FleetFields> {
  RuleResult check(FleetFields input, RuleContext ctx) {
    input.name().startsWith('Atlas') ? RuleResult.failed(ctx.violation().target('name').build()) : RuleResult.passed()
  }
}

/** At most two vehicles in one call. */
@Rule(id = 'fleet.batch-limit')
class BatchLimit implements BusinessRule<Collection<? extends FleetFields>> {
  RuleResult check(Collection<? extends FleetFields> input, RuleContext ctx) {
    input.size() > 2 ? RuleResult.failed(ctx.violation().build()) : RuleResult.passed()
  }
}

/** A vehicle must exist: 404 does not. */
@Rule(id = 'fleet.vehicle-exists')
class VehicleExists implements BusinessRule<Long> {
  RuleResult check(Long input, RuleContext ctx) {
    input == 404L ? RuleResult.failed(ctx.violation().build()) : RuleResult.passed()
  }
}

/** A service whose methods take collections. */
@Service
class FleetService {
  final List<Object> calls = new CopyOnWriteArrayList<>()

  void addAll(List<AddVehicle> vehicles) {
    calls << vehicles
  }

  void addDistinct(Set<AddVehicle> vehicles) {
    calls << vehicles
  }

  void addSome(Collection<? extends FleetFields> vehicles) {
    calls << vehicles
  }

  @Rules(VehicleExists)
  void deleteAll(List<Long> ids) {
    calls << ids
  }

  @Rules(value = [VehicleExists, BatchLimitOfIds], mode = me.yakar.muster.CheckMode.COLLECT_ALL)
  void deleteFew(List<Long> ids) {
    calls << ids
  }

  @Rules(exclude = BatchLimit)
  void addMany(List<AddVehicle> vehicles) {
    calls << vehicles
  }

  void describeAll(List<String> names) {
    calls << names
  }

  @Rules(AmountPositive)
  void payAll(List<Number> amounts) {
    calls << amounts
  }
}

/** An amount must not be negative, whatever kind of number it is. */
@Rule(id = 'fleet.amount-positive')
class AmountPositive implements BusinessRule<Number> {
  RuleResult check(Number input, RuleContext ctx) {
    input.doubleValue() < 0 ? RuleResult.failed(ctx.violation().build()) : RuleResult.passed()
  }
}

/** At most two ids in one call. */
@Rule(id = 'fleet.ids-limit')
class BatchLimitOfIds implements BusinessRule<List<Long>> {
  RuleResult check(List<Long> input, RuleContext ctx) {
    input.size() > 2 ? RuleResult.failed(ctx.violation().build()) : RuleResult.passed()
  }
}

/** A generic base class whose method takes a list of its type argument. */
abstract class Store<T> {
  final List<Object> calls = new CopyOnWriteArrayList<>()

  @Rules
  void storeAll(List<T> items) {
    calls << items
  }
}

/** Stores vehicles: the element type comes from the type argument. */
class VehicleStore extends Store<AddVehicle> {}

/** Not a service: a collection of rule targets checks nothing without rules. */
class FleetComponent {
  final List<Object> calls = []

  void addAll(List<AddVehicle> vehicles) {
    calls << vehicles
  }
}

/** Rules on a raw list. */
class RawList {
  @Rules(VehicleExists)
  @SuppressWarnings('rawtypes')
  void deleteAll(List ids) {}
}

/** Rules on a list of anything. */
class WildcardList {
  @Rules(VehicleExists)
  void deleteAll(List<?> ids) {}
}

/** A collection class whose second type argument is its element type. */
class Pairs<A, B> extends ArrayList<B> {}

/** A collection class of vehicles. */
class Convoy extends ArrayList<AddVehicle> {}

/** A service whose methods take collection classes of their own. */
@Service
class ConvoyService {
  final List<Object> calls = new CopyOnWriteArrayList<>()

  void addConvoy(Convoy convoy) {
    calls << convoy
  }
}

/** Rules on a collection class with a lower bound among its type arguments. */
class LowerBoundPairs {
  @Rules(VehicleExists)
  void deleteAll(Pairs<? super Integer, Long> ids) {}
}

/** A service method that takes a collection of rule targets among other parameters. */
@Service
class OrderService {
  void addLines(Long order, List<AddVehicle> lines) {}
}

/** Rules on a list of a lower bound. */
class LowerBoundList {
  @Rules(VehicleExists)
  void deleteAll(List<? super Long> ids) {}
}

/** Rules on a list of an unbound type variable. */
class OpenList<T> {
  @Rules(VehicleExists)
  void deleteAll(List<T> ids) {}
}

/** Lists a rule that checks neither the elements nor the whole list. */
class WrongRule {
  @Rules(NameFree)
  void deleteAll(List<Long> ids) {}
}
