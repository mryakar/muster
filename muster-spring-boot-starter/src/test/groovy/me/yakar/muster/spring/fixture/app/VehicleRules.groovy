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
package me.yakar.muster.spring.fixture.app

import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import me.yakar.muster.rule.RuleTarget

/** A vehicle, the input of the rules of the application under test. */
record Vehicle(String name) implements RuleTarget {}

/** The names in use, a dependency that the application defines as a bean. */
class Names {
  final Set<String> taken

  Names(Set<String> taken) {
    this.taken = taken
  }
}

/** A rule that the scanner registers, with a dependency injected through its constructor. */
@Rule(id = 'vehicle.name-unique')
class NameUnique implements BusinessRule<Vehicle> {
  final Names names

  NameUnique(Names names) {
    this.names = names
  }

  RuleResult check(Vehicle input, RuleContext ctx) {
    input.name() in names.taken ? RuleResult.failed(ctx.violation().clientArg('name', input.name()).build()) : RuleResult.passed()
  }
}

/** A rule that the application defines with a @Bean method. */
@Rule(id = 'vehicle.name-short')
class NameShort implements BusinessRule<Vehicle> {
  RuleResult check(Vehicle input, RuleContext ctx) {
    input.name().length() <= 20 ? RuleResult.passed() : RuleResult.failed(ctx.violation().build())
  }
}

/** A rule that the application imports. */
@Rule(id = 'vehicle.name-capitalized')
class NameCapitalized implements BusinessRule<Vehicle> {
  RuleResult check(Vehicle input, RuleContext ctx) {
    Character.isUpperCase(input.name().charAt(0)) ? RuleResult.passed() : RuleResult.failed(ctx.violation().build())
  }
}

/** Not a rule class: abstract. */
@Rule(id = 'vehicle.abstract')
abstract class AbstractRule implements BusinessRule<Vehicle> {}

/** Not a rule class: no business rule. */
@Rule(id = 'vehicle.not-a-rule')
class NotARule {}

/** Not a rule class: no @Rule. */
class Unannotated implements BusinessRule<Vehicle> {
  RuleResult check(Vehicle input, RuleContext ctx) {
    RuleResult.passed()
  }
}
