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
package me.yakar.muster

import java.util.logging.Level
import me.yakar.muster.EngineSpecification.NameUnique
import me.yakar.muster.EngineSpecification.VehicleFields
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import me.yakar.muster.spi.RuleFactory

/** Building a Muster: registering rules, creating them, and switching them off. */
class MusterSpec extends EngineSpecification {

  @Rule(id = 'vehicle.name-unique')
  static class NameUniqueAgain implements BusinessRule<VehicleFields> {
    RuleResult check(VehicleFields input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static class Unannotated implements BusinessRule<Object> {
    RuleResult check(Object input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static class ProxiedNameUnique extends NameUnique {}

  /** Throws without wrapping, as a factory in another JVM language may, checked exceptions included. */
  static class ThrowingFactory implements RuleFactory {
    final Exception failure

    ThrowingFactory(Exception failure) {
      this.failure = failure
    }

    BusinessRule<?> create(Class<? extends BusinessRule<?>> ruleClass) {
      throw failure
    }
  }

  static final RuleFactory NEVER_CALLED = {
    throw new AssertionError('not called')
  } as RuleFactory

  def 'Muster logs every disabled rule when it is built'() {
    when:
    Muster.builder().rules(new NameUnique(), new CodeFormat()).disable('vehicle.name-unique', 'vehicle.code-format').build()

    then:
    logged*.level == [Level.WARNING, Level.WARNING]
    logged*.message == [
      "Rule 'vehicle.name-unique' is disabled",
      "Rule 'vehicle.code-format' is disabled"
    ]
  }

  def 'a disabled rule is still created, so that switching it on cannot reveal a setup error'() {
    given:
    def created = []
    RuleFactory factory = {
      created << it; it.getDeclaredConstructor().newInstance()
    }

    when:
    Muster.builder().rules(factory, NameUnique, CodeFormat).disable('vehicle.name-unique').build()

    then:
    created == [NameUnique, CodeFormat]
  }

  def 'a disabled rule still has to have its required rules registered'() {
    when:
    Muster.builder().rules(new TaskAssignable(ran)).disable('task.assignable').build()

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "${TaskAssignable.name} requires ${TaskExists.name}, which is not registered"
  }

  def 'a rule that requires a disabled rule must be disabled as well'() {
    when:
    Muster.builder().rules(new TaskExists(ran), new TaskAssignable(ran)).disable('task.exists').build()

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "rules that require disabled rules are enabled: 'task.assignable' requires 'task.exists'; disable them as well, or enable what they require"
  }

  def 'disabling an unknown id is rejected, so that a typo in configuration is not ignored'() {
    when:
    Muster.builder().rules(new NameUnique()).disable('vehicle.nme-unique').build()

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "cannot disable 'vehicle.nme-unique': no rule has that id"
  }

  def 'Muster checks the rule requirements when it is built'() {
    when:
    Muster.builder().rules(new TaskAssignable(ran)).build()

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "${TaskAssignable.name} requires ${TaskExists.name}, which is not registered"
  }

  def 'a factory creates each rule once, when Muster is built'() {
    given:
    def created = []
    RuleFactory factory = {
      created << it; it.getDeclaredConstructor().newInstance()
    }
    def builder = Muster.builder().rules(factory, NameUnique, CodeFormat)

    expect:
    created.empty

    when:
    def muster = builder.build()
    def chain = muster.chain(CreateVehicle, NameUnique, CodeFormat)
    chain.check(VALID)
    chain.check(VALID)

    then:
    created == [NameUnique, CodeFormat]
  }

  def 'a factory may return a subclass, such as a proxy'() {
    given:
    RuleFactory factory = { new ProxiedNameUnique() }

    when:
    Muster.builder().rules(factory, NameUnique).build().chain(CreateVehicle, NameUnique).check(INVALID)

    then:
    thrown(BusinessRuleViolationException)
  }

  def 'a factory that fails with #failure stops the build'() {
    given:
    def factory = new ThrowingFactory(failure)

    when:
    Muster.builder().rules(factory, NameUnique).build()

    then:
    def e = thrown(IllegalStateException)
    e.message == "the rule factory could not create ${NameUnique.name}"
    e.cause.is(failure)

    where:
    failure << [
      new IllegalStateException('no such bean'),
      new IOException('disk unavailable')
    ]
  }

  def 'a factory that returns #returned stops the build'() {
    when:
    Muster.builder().rules(factory as RuleFactory, NameUnique).build()

    then:
    def e = thrown(IllegalStateException)
    e.message == message

    where:
    returned            | factory || message
    'null'              | {
      null
    } || "the rule factory returned null for ${NameUnique.name}"
    'another rule'      | {
      new CodeFormat()
    } || "the rule factory returned a ${CodeFormat.name} for ${NameUnique.name}"
  }

  def 'registering #problem is rejected'() {
    when:
    Muster.builder().rules(first, second)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == message

    where:
    problem                      | first             | second || message
    'an unannotated rule'        | new Unannotated() | new NameUnique() || "${Unannotated.name} is not annotated with @Rule"
    'a rule twice'               | new NameUnique()  | new NameUnique() || "${NameUnique.name} is registered twice"
    'two rules with the same id' | new NameUnique()  | new NameUniqueAgain() || "${NameUnique.name} and ${NameUniqueAgain.name} share the id 'vehicle.name-unique'"
  }

  def 'a rule class registered as an instance cannot be registered with a factory too'() {
    when:
    Muster.builder().rules(new NameUnique()).rules(NEVER_CALLED, NameUnique)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "${NameUnique.name} is registered twice"
  }

  def 'a builder rejects a null #message'() {
    when:
    configure(Muster.builder())

    then:
    def e = thrown(NullPointerException)
    e.message == message

    where:
    message      | configure
    'rule'       | { it.rules((BusinessRule) null) }
    'factory'    | { it.rules((RuleFactory) null, NameUnique) }
    'rule class' | { it.rules(NEVER_CALLED, (Class) null) }
    'caller'     | { it.caller(null) }
    'locale'     | { it.locale(null) }
    'id'         | { it.disable('task.exists', null) }
    'mode'       | { it.mode(null) }
  }
}
