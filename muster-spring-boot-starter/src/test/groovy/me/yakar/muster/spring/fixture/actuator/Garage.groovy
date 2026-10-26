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
package me.yakar.muster.spring.fixture.actuator

import me.yakar.muster.CheckMode
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.RequiresRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import me.yakar.muster.rule.RuleTarget
import me.yakar.muster.spring.Rules
import org.springframework.stereotype.Service

/** What the garage rules check: a vehicle with a plate. */
interface Plated extends RuleTarget {
  String plate()
}

/** A car, final as a record is. */
record Car(String plate) implements Plated {}

/** A truck, with rules of its own. */
record Truck(String plate, int axles) implements Plated {}

/** A plate must not start with "bad". */
@Rule(id = 'garage.plate-format')
class PlateFormat implements BusinessRule<Plated> {
  RuleResult check(Plated input, RuleContext ctx) {
    input.plate().startsWith('bad') ? RuleResult.failed(ctx.violation().build()) : RuleResult.passed()
  }
}

/** A plate must be free, once its format passed. */
@Rule(id = 'garage.plate-free')
@RequiresRule(PlateFormat)
class PlateFree implements BusinessRule<Plated> {
  RuleResult check(Plated input, RuleContext ctx) {
    RuleResult.passed()
  }
}

/** A truck has at most four axles. */
@Rule(id = 'garage.axles')
class AxlesLimit implements BusinessRule<Truck> {
  RuleResult check(Truck input, RuleContext ctx) {
    input.axles() > 4 ? RuleResult.failed(ctx.violation().build()) : RuleResult.passed()
  }
}

/** Rejects everything; the tests switch it off. */
@Rule(id = 'garage.closed')
class GarageClosed implements BusinessRule<Plated> {
  RuleResult check(Plated input, RuleContext ctx) {
    RuleResult.failed(ctx.violation().build())
  }
}

/** At most two vehicles in one call. */
@Rule(id = 'garage.batch-limit')
class BatchLimit implements BusinessRule<Collection<? extends Plated>> {
  RuleResult check(Collection<? extends Plated> input, RuleContext ctx) {
    input.size() > 2 ? RuleResult.failed(ctx.violation().build()) : RuleResult.passed()
  }
}

/** A vehicle id must be known: 404 is not. */
@Rule(id = 'garage.id-known')
class IdKnown implements BusinessRule<Long> {
  RuleResult check(Long input, RuleContext ctx) {
    input == 404L ? RuleResult.failed(ctx.violation().build()) : RuleResult.passed()
  }
}

/** A service with advised methods of every kind. */
@Service
class GarageService {
  /** A type that is not final: the chain of each class is bound on its first check. */
  void park(Plated vehicle) {}

  /** A final type: its one chain is bound at startup. */
  void register(Car car) {}

  @Rules(value = IdKnown, mode = CheckMode.COLLECT_ALL)
  void remove(Long id) {}

  @Rules(exclude = PlateFree)
  void inspect(Car car) {}

  void parkAll(List<Car> cars) {}
}

/** An interface whose method a proxy may be called by. */
interface Washing {
  void wash(Car car)
}

/** A service called through its interface and its class, one advice for both. */
@Service
class WashService implements Washing {
  void wash(Car car) {}
}
