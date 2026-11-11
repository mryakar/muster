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
package me.yakar.muster.spring.fixture.values

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicInteger
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.RequiresRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import me.yakar.muster.rule.ValueRule
import me.yakar.muster.spring.CheckedValues
import me.yakar.muster.spring.Rules
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/** A vehicle, as the garage stores it. */
record Vehicle(Long id, String owner) {}

/** Vehicles 1 to 99 exist, owned by amo except the even ones above 10; counts its reads. */
class Garage {
  final AtomicInteger reads = new AtomicInteger()

  Optional<Vehicle> find(Long id) {
    reads.incrementAndGet()
    id in 1L..99L ? Optional.of(new Vehicle(id, id> 10 && id % 2 == 0 ? 'ece' : 'amo')) : Optional.empty()
  }
}

/** A vehicle must exist; leaves the vehicle it found. */
@Rule(id = 'values.vehicle-found')
@RequiresRule([])
class VehicleFound implements ValueRule<Long, Vehicle> {
  final Garage garage

  VehicleFound(Garage garage) {
    this.garage = garage
  }

  RuleResult check(Long input, RuleContext ctx) {
    garage.find(input).map {
      RuleResult.passed(it)
    }.orElseGet {
      RuleResult.failed(ctx.violation().build())
    }
  }
}

/** A vehicle must be amo's; reads the vehicle that VehicleFound left. */
@Rule(id = 'values.vehicle-owned')
@RequiresRule(VehicleFound)
class VehicleOwned implements BusinessRule<Long> {
  RuleResult check(Long input, RuleContext ctx) {
    ctx.valueOf(VehicleFound).owner() == 'amo' ? RuleResult.passed() : RuleResult.failed(ctx.violation().build())
  }
}

/** Finds a parking spot, as its number; applies only while the garage has spots. */
@Rule(id = 'values.spot-found')
@RequiresRule([])
class SpotFound implements ValueRule<Long, Integer> {
  volatile boolean applies = true

  boolean appliesTo(RuleContext ctx) {
    applies
  }

  RuleResult check(Long input, RuleContext ctx) {
    RuleResult.passed(input.intValue())
  }
}

/** An id must be positive; leaves no value. */
@Rule(id = 'values.id-positive')
@RequiresRule([])
class IdPositive implements BusinessRule<Long> {
  RuleResult check(Long input, RuleContext ctx) {
    input> 0 ? RuleResult.passed() : RuleResult.failed(ctx.violation().build())
  }
}

/** Wrongly reads the bean while it is checked, and notes what it found. */
@Rule(id = 'values.peeks')
@RequiresRule(VehicleFound)
class PeeksAtValues implements BusinessRule<Long> {
  final CheckedValues values
  final List<String> seen = new CopyOnWriteArrayList<>()

  PeeksAtValues(CheckedValues values) {
    this.values = values
  }

  RuleResult check(Long input, RuleContext ctx) {
    seen << values.toString()
    try {
      seen << values.valueOf(VehicleFound).toString()
    } catch (IllegalStateException e) {
      seen << e.message
    }
    RuleResult.passed()
  }
}

/** An {@code @Async} method with rules, which checks them on the thread that runs it. */
@Service
class LaterService {
  final CheckedValues values

  LaterService(CheckedValues values) {
    this.values = values
  }

  @Async
  @Rules(VehicleOwned)
  CompletableFuture<Vehicle> renameLater(Long id) {
    CompletableFuture.completedFuture(values.valueOf(VehicleFound))
  }
}

/** A service called from within another's advised methods. */
@Service
class InnerService {
  final CheckedValues values

  InnerService(CheckedValues values) {
    this.values = values
  }

  @Rules(VehicleOwned)
  Vehicle find(Long id) {
    values.valueOf(VehicleFound)
  }

  /** Its rule reads the bean while it is checked. */
  @Rules(PeeksAtValues)
  Long peek(Long id) {
    id
  }

  /** Its rules leave no value, so it cannot read those of the method that called it. */
  @Rules(IdPositive)
  Vehicle findWithoutValues(Long id) {
    values.valueOf(VehicleFound)
  }
}

/** A service that reads the values of its rules. */
@Service
class VehicleService {
  final CheckedValues values
  final InnerService inner

  VehicleService(CheckedValues values, InnerService inner) {
    this.values = values
    this.inner = inner
  }

  @Rules(VehicleOwned)
  Vehicle rename(Long id) {
    values.valueOf(VehicleFound)
  }

  @Rules(VehicleOwned)
  List<Vehicle> renameAll(List<Long> ids) {
    (0..<ids.size()).collect { values.element(it).valueOf(VehicleFound) }
  }

  /** Reads its own vehicle around a call to an advised method that reads the next one. */
  @Rules(VehicleOwned)
  List<Vehicle> renameAround(Long id) {
    def before = values.valueOf(VehicleFound)
    def inside = inner.find(id + 2)
    [before, inside, values.valueOf(VehicleFound)]
  }

  /** Calls an advised method whose rule reads the bean while it is checked. */
  @Rules(VehicleOwned)
  Long renameAndPeek(Long id) {
    inner.peek(id + 2)
  }

  /** Calls an advised method whose rules leave no value. */
  @Rules(VehicleOwned)
  Vehicle renameThroughNoValues(Long id) {
    inner.findWithoutValues(id)
  }

  /** Reads the values on another thread, as an {@code @Async} method would. */
  @Rules(VehicleOwned)
  Throwable renameElsewhere(Long id) {
    try {
      CompletableFuture.supplyAsync { values.valueOf(VehicleFound) }.get()
      null
    } catch (ExecutionException e) {
      e.cause
    }
  }

  /** Fails after its check passed. */
  @Rules(VehicleOwned)
  Vehicle renameAndFail(Long id) {
    throw new IllegalArgumentException('renaming failed')
  }

  /** Tells whether the vehicle was found, as a method whose rules may not apply would ask. */
  @Rules(VehicleOwned)
  boolean renameIfFound(Long id) {
    values.hasValue(VehicleFound)
  }

  /** Tells whether a spot was found, which its rule does only while it applies. */
  @Rules(SpotFound)
  boolean parkIfFound(Long id) {
    values.hasValue(SpotFound)
  }

  /** Tells what the bean says of itself while it runs. */
  @Rules(VehicleOwned)
  String describe(Long id) {
    values.toString()
  }
}
