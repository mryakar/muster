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

import static me.yakar.muster.Concurrency.concurrently

import groovy.transform.InheritConstructors
import java.util.logging.Level
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.RequiresRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult

/** Bindings: listed rules together with the rules that bind by type, less the excluded ones. */
class BindingSpec extends EngineSpecification {

  static final String FIELDS = VehicleFields.name
  static final String CREATE = CreateVehicle.name
  static final String UPDATE = UpdateVehicle.name

  /** A rule that notes when it runs and passes. */
  static abstract class Noted<T> implements BusinessRule<T> {
    final List<String> ran

    Noted(List<String> ran) {
      this.ran = ran
    }

    RuleResult check(T input, RuleContext ctx) {
      ran << ctx.ruleId()
      RuleResult.passed()
    }
  }

  @Rule(id = 'fields.listed')
  @InheritConstructors
  static class FieldsListed extends Noted<EngineSpecification.VehicleFields> {}

  @Rule(id = 'fields.bound')
  @InheritConstructors
  static class FieldsBound extends Noted<EngineSpecification.VehicleFields> {}

  @Rule(id = 'fields.base')
  @InheritConstructors
  static class FieldsBase extends Noted<EngineSpecification.VehicleFields> {}

  @Rule(id = 'fields.needs-base')
  @RequiresRule(FieldsBase)
  @InheritConstructors
  static class FieldsNeedsBase extends Noted<EngineSpecification.VehicleFields> {}

  @Rule(id = 'fields.top')
  @RequiresRule(FieldsNeedsBase)
  @InheritConstructors
  static class FieldsTop extends Noted<EngineSpecification.VehicleFields> {}

  @Rule(id = 'fields.diamond')
  @RequiresRule([FieldsNeedsBase, FieldsTop])
  @InheritConstructors
  static class FieldsDiamond extends Noted<EngineSpecification.VehicleFields> {}

  @Rule(id = 'identified.bound')
  @InheritConstructors
  static class IdentifiedBound extends Noted<EngineSpecification.Identified> {}

  @Rule(id = 'long.listed')
  @InheritConstructors
  static class LongListed extends Noted<Long> {}

  @Rule(id = 'long.bound')
  @InheritConstructors
  static class LongBound extends Noted<Long> {}

  @Rule(id = 'long.base')
  @InheritConstructors
  static class LongBase extends Noted<Long> {}

  @Rule(id = 'long.needs-base')
  @RequiresRule(LongBase)
  @InheritConstructors
  static class LongNeedsBase extends Noted<Long> {}

  Muster.Builder builder(Class<?>... ruleClasses) {
    Muster.builder().rules({
      it.getDeclaredConstructor(List).newInstance(ran)
    }, *ruleClasses)
  }

  Muster muster(Class<?>... ruleClasses) {
    builder(ruleClasses).build()
  }

  def 'a binding runs the listed rules, then the rules that bind by type to the class of the input'() {
    given:
    def binding = muster(FieldsListed, FieldsBound, IdentifiedBound).binding(VehicleFields).rules(FieldsListed).build()

    when:
    binding.check(new CreateVehicle('TR-1', 'Atlas'))

    then:
    ran == ['fields.listed', 'fields.bound']

    when:
    ran.clear()
    binding.check(new UpdateVehicle(1L, 'TR-1', 'Atlas'))

    then:
    ran == ['fields.listed', 'fields.bound', 'identified.bound']
  }

  def 'for an input that is not a rule target, a binding runs only the listed rules'() {
    given:
    def binding = muster(LongListed, LongBound).binding(type).rules(LongListed).build()

    when:
    binding.check(1L)

    then:
    ran == ['long.listed']

    where:
    type << [Long, long]
  }

  def 'the listed rules and their requirements run first; the rules bound by type follow, after theirs, in the order of their ids'() {
    given:
    def binding = muster(FieldsListed, FieldsBound, FieldsBase, FieldsNeedsBase, FieldsTop).binding(VehicleFields).rules(FieldsTop, FieldsListed).build()

    when:
    binding.check(VALID)

    then:
    ran == [
      'fields.base',
      'fields.needs-base',
      'fields.top',
      'fields.listed',
      'fields.bound'
    ]
  }

  def 'a rule bound by type that requires no excluded rule, along any of its paths, stays'() {
    given:
    def binding = muster(FieldsListed, FieldsBound, FieldsBase, FieldsNeedsBase, FieldsTop, FieldsDiamond).binding(VehicleFields).rules(FieldsListed).exclude(FieldsBound).build()

    when:
    binding.check(VALID)

    then:
    ran == [
      'fields.listed',
      'fields.base',
      'fields.needs-base',
      'fields.top',
      'fields.diamond'
    ]
    logged.empty
  }

  def 'an excluded rule does not bind by type'() {
    given:
    def binding = muster(FieldsListed, FieldsBound).binding(VehicleFields).rules(FieldsListed).exclude(FieldsBound).build()

    when:
    binding.check(VALID)

    then:
    ran == ['fields.listed']
  }

  def 'a rule bound by type that requires an excluded rule is left out too, and logged once for each class'() {
    given:
    def binding = muster(FieldsListed, FieldsBase, FieldsNeedsBase, FieldsTop).binding(VehicleFields).rules(FieldsListed).exclude(FieldsBase).build()

    when:
    binding.check(VALID)
    binding.check(VALID)

    then:
    ran == ['fields.listed', 'fields.listed']
    logged*.message == [
      "The chain for ${FIELDS} (${CREATE}) leaves out rules that require excluded rules: 'fields.needs-base' requires 'fields.base'; 'fields.top' requires 'fields.base'"
    ]
    logged*.level == [Level.INFO]
  }

  def 'a listed rule that requires an excluded rule is rejected when the binding is built: #listedRule.simpleName'() {
    when:
    muster(FieldsBase, FieldsNeedsBase, FieldsTop).binding(VehicleFields).rules(listedRule).exclude(FieldsBase).build()

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "listed rules require excluded rules, directly or through other rules: '${id}' requires 'fields.base'; list other rules, or stop excluding what they require"

    where:
    listedRule || id
    FieldsNeedsBase || 'fields.needs-base'
    FieldsTop || 'fields.top'
  }

  def 'a disabled listed rule may require an excluded rule'() {
    given:
    def muster = builder(FieldsBase, FieldsNeedsBase, FieldsListed).disable('fields.needs-base').build()

    when:
    muster.binding(VehicleFields).rules(FieldsNeedsBase, FieldsListed).exclude(FieldsBase).build().check(VALID)

    then:
    ran == ['fields.listed']
  }

  def 'a binding leaves out disabled rules, listed or bound by type'() {
    given:
    def muster = builder(FieldsListed, FieldsBound, FieldsBase).disable('fields.listed', 'fields.bound').build()

    when:
    muster.binding(VehicleFields).rules(FieldsListed).build().check(VALID)

    then:
    ran == ['fields.base']
  }

  def 'a binding rejects #problem when it is built'() {
    when:
    def builder = muster(FieldsListed, FieldsBound, LongListed).binding(VehicleFields)
    configure(builder)
    builder.build()

    then:
    def e = thrown(IllegalArgumentException)
    e.message == message

    where:
    problem                       | configure || message
    'a listed rule not registered' | {
      it.rules(FieldsBase)
    } || "${FieldsBase.name} is not registered"
    'a listed rule of another type' | {
      it.rules(LongListed)
    } || "${LongListed.name} checks java.lang.Long, which ${FIELDS} is not"
    'an excluded rule not registered' | {
      it.exclude(FieldsBase)
    } || "${FieldsBase.name} is not registered"
    'a rule listed and excluded'  | {
      it.rules(FieldsBound).exclude(FieldsBound)
    } || "${FieldsBound.name} is both listed and excluded"
  }

  def 'a binding rejects a rule #what twice'() {
    when:
    muster(FieldsListed).binding(VehicleFields)."$method"(FieldsListed, FieldsListed)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "${FieldsListed.name} is ${what} twice"

    where:
    method    | what
    'rules'   | 'listed'
    'exclude' | 'excluded'
  }

  def 'a binding rejects a null #what'() {
    when:
    configure(muster(FieldsListed))

    then:
    def e = thrown(NullPointerException)
    e.message == what

    where:
    what         | configure
    'inputType'  | { it.binding(null) }
    'rule class' | { it.binding(VehicleFields).rules(FieldsListed, null) }
    'rule class' | { it.binding(VehicleFields).exclude(FieldsListed, null) }
    'mode'       | { it.binding(VehicleFields).mode(null) }
    'name'       | { it.binding(VehicleFields).name(null) }
  }

  def 'a binding rejects a blank name'() {
    when:
    muster(FieldsListed).binding(VehicleFields).name(' ')

    then:
    def e = thrown(IllegalArgumentException)
    e.message == 'name must not be blank'
  }

  def 'a binding checks in its own mode, or else in the mode of the Muster'() {
    given:
    def muster = Muster.builder().rules(new NameUnique(), new CodeFormat()).build()
    def builder = muster.binding(VehicleFields)
    if (mode != null) {
      builder.mode(mode)
    }
    def binding = builder.build()

    when:
    binding.check(INVALID)

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.ruleId() == reported
    binding.mode() == (mode ?: CheckMode.FAIL_FAST)

    where:
    mode || reported
    null || ['vehicle.code-format']
    CheckMode.COLLECT_ALL || ['vehicle.code-format', 'vehicle.name-unique']
  }

  def 'a binding for a final type prepares its rules when it is built, and logs them under its name'() {
    given:
    log.level = Level.FINE
    def muster = muster(FieldsBase, FieldsNeedsBase, FieldsBound)

    when:
    def binding = muster.binding(CreateVehicle).rules(FieldsNeedsBase).name('VehicleService.create').build()

    then:
    logged*.message == [
      "Rules bound to VehicleService.create (${CREATE}): [fields.base, fields.needs-base, fields.bound]"
    ]

    when:
    logged.clear()
    binding.check(VALID)
    binding.check(VALID)

    then:
    logged.empty
    ran == [
      'fields.base',
      'fields.needs-base',
      'fields.bound',
      'fields.base',
      'fields.needs-base',
      'fields.bound'
    ]
  }

  def 'a binding names itself when it logs a rule that its chain adds'() {
    when:
    muster(LongBase, LongNeedsBase).binding(Long).rules(LongNeedsBase).name('TaskService.delete').build()

    then:
    logged*.message == [
      "The chain for TaskService.delete (java.lang.Long) includes rule 'long.base' because 'long.needs-base' requires it"
    ]
  }

  def 'a binding for another type prepares the rules of a class on its first check, once'() {
    given:
    log.level = Level.FINE
    def binding = muster(FieldsListed, IdentifiedBound).binding(VehicleFields).rules(FieldsListed).build()

    expect:
    logged.empty

    when:
    binding.check(VALID)
    binding.check(VALID)
    binding.check(new UpdateVehicle(1L, 'TR-1', 'Atlas'))

    then:
    logged*.message == [
      "Rules bound to ${FIELDS} (${CREATE}): [fields.listed]",
      "Rules bound to ${FIELDS} (${UPDATE}): [fields.listed, identified.bound]"
    ]
  }

  def 'a binding warns when no rule binds'() {
    when:
    muster(LongListed).binding(Long).build()

    then:
    logged*.message == [
      'No rule binds to java.lang.Long; check that its rules are registered, enabled and accept it'
    ]
  }

  def 'a binding rejects an input of another type, which only unchecked code can pass'() {
    given:
    Binding raw = muster(FieldsListed).binding(VehicleFields).build()

    when:
    raw.check('text')

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "the binding checks ${FIELDS}, not java.lang.String"
  }

  def 'a binding rejects a null input'() {
    when:
    muster(FieldsListed).binding(VehicleFields).build().check(null)

    then:
    def e = thrown(NullPointerException)
    e.message == 'input'
  }

  def 'a binding describes itself for logs'() {
    expect:
    muster(FieldsListed, FieldsBound, FieldsBase).binding(VehicleFields).rules(FieldsListed).exclude(FieldsBound, FieldsBase).mode(CheckMode.COLLECT_ALL).name('VehicleService.save').build().toString() ==
        "Binding[name=VehicleService.save, input=${FIELDS}, mode=COLLECT_ALL, rules=[fields.listed], excluded=[fields.base, fields.bound]]"
  }

  def 'threads that check a new class at the same time bind its rules once, and each runs them'() {
    given:
    log.level = Level.FINE
    def binding = muster(FieldsListed, FieldsBound).binding(VehicleFields).rules(FieldsListed).build()

    when:
    concurrently { binding.check(VALID) }

    then:
    ran.size() == 32
    logged*.message == ["Rules bound to ${FIELDS} (${CREATE}): [fields.listed, fields.bound]"]
  }

  def 'a binding for another type tells the chains of the classes checked so far, by name, as a copy'() {
    given:
    def binding = muster(FieldsListed, IdentifiedBound).binding(VehicleFields).rules(FieldsListed).build()

    expect:
    binding.chains().isEmpty()

    when:
    binding.check(new UpdateVehicle(1L, 'TR-1', 'Atlas'))
    def one = binding.chains()
    binding.check(VALID)
    def two = binding.chains()

    then:
    one.keySet().toList() == [UpdateVehicle]
    two.keySet().toList() == [CreateVehicle, UpdateVehicle]
    two.values()*.ruleIds() == [['fields.listed'], ['fields.listed', 'identified.bound']]

    when:
    two.clear()

    then:
    thrown(UnsupportedOperationException)
  }

  def 'a binding for a final type tells its one chain, bound when it was built'() {
    given:
    def binding = muster(FieldsListed, FieldsBound).binding(CreateVehicle).rules(FieldsListed).build()

    expect:
    binding.chains().keySet().toList() == [CreateVehicle]
    binding.chains()[CreateVehicle].ruleIds() == ['fields.listed', 'fields.bound']
  }

  def 'threads read the chains checked so far while others bind new classes'() {
    given:
    def binding = muster(FieldsListed, IdentifiedBound).binding(VehicleFields).rules(FieldsListed).build()

    when:
    def seen = concurrently { n ->
      binding.check(n % 2 == 0 ? VALID : new UpdateVehicle(1L, 'TR-1', 'Atlas'))
      binding.chains().keySet().toList()
    }

    then:
    seen.every { it in [[CreateVehicle], [UpdateVehicle], [CreateVehicle, UpdateVehicle]] }
    binding.chains().keySet().toList() == [CreateVehicle, UpdateVehicle]
  }
}
