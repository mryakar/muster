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
package me.yakar.muster.spring.fixture.observation

import io.micrometer.observation.ObservationRegistry
import java.util.concurrent.CopyOnWriteArrayList
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.RequiresRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import me.yakar.muster.rule.RuleTarget
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service

/** A shipment, the input of the observed rules. */
record Shipment(String code) implements RuleTarget {}

/** Fails a code that starts with "bad". */
@Rule(id = 'shipment.code-format')
class CodeFormat implements BusinessRule<Shipment> {
  RuleResult check(Shipment input, RuleContext ctx) {
    input.code().startsWith('bad') ? RuleResult.failed(ctx.violation().build()) : RuleResult.passed()
  }
}

/** Fails the code "TAKEN", and throws for the code "BOOM"; runs once the format passed. */
@Rule(id = 'shipment.code-free')
@RequiresRule(CodeFormat)
class CodeFree implements BusinessRule<Shipment> {
  static final IllegalStateException FAILURE = new IllegalStateException('registry down')

  RuleResult check(Shipment input, RuleContext ctx) {
    if (input.code() == 'BOOM') {
      throw FAILURE
    }
    input.code() == 'TAKEN' ? RuleResult.failed(ctx.violation().build()) : RuleResult.passed()
  }
}

/** Applies to no call. */
@Rule(id = 'shipment.imported')
class Imported implements BusinessRule<Shipment> {
  boolean appliesTo(RuleContext ctx) {
    false
  }

  RuleResult check(Shipment input, RuleContext ctx) {
    RuleResult.failed(ctx.violation().build())
  }
}

/** Notes the observation in scope while it runs, and its parent, as what a rule calls would see. */
@Rule(id = 'shipment.traced')
class Traced implements BusinessRule<Shipment> {
  final ObservationRegistry registry
  final List<List<String>> seen = new CopyOnWriteArrayList<>()

  Traced(ObjectProvider<ObservationRegistry> registry) {
    this.registry = registry.getIfAvailable { ObservationRegistry.NOOP }
  }

  RuleResult check(Shipment input, RuleContext ctx) {
    def current = registry.currentObservation
    seen << [current?.context?.name, current?.context?.parentObservation?.context?.name]
    RuleResult.passed()
  }
}

/** A service whose public method is checked by the rules of its shipment. */
@Service
class ShipmentService {
  void create(Shipment shipment) {
  }
}
