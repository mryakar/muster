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

import me.yakar.muster.EngineSpecification.VehicleFields
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.RequiresRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import me.yakar.muster.rule.ValueRule
import me.yakar.muster.spi.CheckListener

class TargetNameSpec extends EngineSpecification {

  /** Reports the name of a taken vehicle, a part of it, and the vehicle itself. */
  @Rule(id = 'vehicle.name-free')
  static class NameFree implements BusinessRule<VehicleFields> {
    RuleResult check(VehicleFields input, RuleContext ctx) {
      input.name() == 'taken'
          ? RuleResult.failed(
          ctx.violation().target('name').build(),
          ctx.violation('vehicle.name-word').target('name.first').build(),
          ctx.violation('vehicle.name-taken').build())
          : RuleResult.passed()
    }
  }

  @Rule(id = 'vehicle.code-free')
  static class CodeFree implements BusinessRule<VehicleFields> {
    RuleResult check(VehicleFields input, RuleContext ctx) {
      input.code() == 'bad' ? RuleResult.failed(ctx.violation().target('code').build()) : RuleResult.passed()
    }
  }

  @Rule(id = 'vehicle.name-read')
  static class NameRead implements ValueRule<VehicleFields, String> {
    RuleResult check(VehicleFields input, RuleContext ctx) {
      RuleResult.passed(input.name())
    }
  }

  /** Reads the value of a rule it requires, so that its context reads values. */
  @Rule(id = 'vehicle.name-allowed')
  @RequiresRule(NameRead)
  static class NameAllowed implements BusinessRule<VehicleFields> {
    RuleResult check(VehicleFields input, RuleContext ctx) {
      ctx.valueOf(NameRead) == 'taken' ? RuleResult.failed(ctx.violation().target('name').build()) : RuleResult.passed()
    }
  }

  /** Reports from a context it builds again from the one it receives. */
  @Rule(id = 'vehicle.name-rebuilt')
  static class NameRebuilt implements BusinessRule<VehicleFields> {
    RuleResult check(VehicleFields input, RuleContext ctx) {
      RuleResult.failed(ctx.toBuilder().locale(Locale.ENGLISH).build().violation().target('name').build())
    }
  }

  /** A rule of the whole collection, about one element and about the collection. */
  @Rule(id = 'vehicle.names-unique')
  static class NamesUnique implements BusinessRule<List<VehicleFields>> {
    RuleResult check(List<VehicleFields> input, RuleContext ctx) {
      RuleResult.failed(ctx.element(1).violation().target('name').build(), ctx.violation().target('name').build())
    }
  }

  static final CreateVehicle TAKEN = new CreateVehicle('bad', 'taken')

  def muster = Muster.builder().mode(CheckMode.COLLECT_ALL)
  .rules(new NameFree(), new CodeFree(), new NameRead(), new NameAllowed(), new NameRebuilt(), new NamesFree(),
  new NamesUnique())
  .build()

  /** A Muster with only the given rules, since a binding also binds the rules of its type. */
  static Muster only(BusinessRule<?>... rules) {
    Muster.builder().mode(CheckMode.COLLECT_ALL).rules(rules).build()
  }

  def 'a chain names the targets it renames, and the targets inside them'() {
    given:
    def chain = muster.chain(VehicleFields, NameFree, CodeFree).withTarget('name', 'vehicleName')

    when:
    chain.check(TAKEN)

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.target()*.orElse('-') == ['vehicleName', 'vehicleName.first', '-', 'code']
  }

  def 'the chain it came from keeps the targets of the rules'() {
    given:
    def chain = muster.chain(VehicleFields, NameFree)
    chain.withTarget('name', 'vehicleName')

    when:
    chain.check(TAKEN)

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.target()*.orElse('-') == ['name', 'name.first', '-']
  }

  def 'a chain renames several targets, the longest that matches first'() {
    given:
    def chain = muster.chain(VehicleFields, NameFree, CodeFree)
        .withTarget('name', 'vehicleName')
        .withTarget('name.first', 'firstName')
        .withTarget('code', 'vehicleCode')

    when:
    chain.check(TAKEN)

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.target()*.orElse('-') == ['vehicleName', 'firstName', '-', 'vehicleCode']
  }

  def 'a chain keeps its names when its mode or name changes'() {
    given:
    def checks = []
    def ends = []
    def listened = Muster.builder().rules(new NameFree()).listener({ check ->
      checks << check
      new CheckListener.Observer() {
            void checkEnded(CheckListener.CheckEnd end) {
              ends << end
            }
          }
    }).build()
    def chain = listened.chain(VehicleFields, NameFree).withTarget('name', 'vehicleName')
        .withMode(CheckMode.FAIL_FAST).withName('audit')

    when:
    chain.check(TAKEN)

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.target()*.orElse('-') == ['vehicleName', 'vehicleName.first', '-']
    checks*.name() == ['audit']
    ends[0].violations()*.target()*.orElse('-') == ['vehicleName', 'vehicleName.first', '-']
    chain.mode() == CheckMode.FAIL_FAST
  }

  def 'a chain renames the targets that a rule reading values reports'() {
    given:
    def chain = muster.chain(VehicleFields, NameAllowed).withTarget('name', 'vehicleName')

    when:
    chain.check(TAKEN)

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == ['vehicle.name-allowed vehicleName']
  }

  def 'a context built again from the one a rule receives keeps the names'() {
    when:
    muster.chain(VehicleFields, NameRebuilt).withTarget('name', 'vehicleName').check(VALID)

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == ['vehicle.name-rebuilt vehicleName']
  }

  def 'in a check of a collection the position stays in front of the renamed target'() {
    given:
    def chain = muster.chain(VehicleFields, NameFree, NamesFree, NameAllowed).withTarget('name', 'vehicleName')

    when:
    chain.checkAll([VALID, TAKEN])

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == [
      'vehicle.name-free [1].vehicleName',
      'vehicle.name-free [1].vehicleName.first',
      'vehicle.name-free [1]',
      'vehicle.names-free [1].vehicleName',
      'vehicle.name-allowed [1].vehicleName'
    ]
  }

  def 'a rule of the whole collection reports renamed targets'() {
    when:
    only(new NamesUnique()).collectionBinding(VehicleFields).rules(NamesUnique).target('name', 'vehicleName').build()
        .check([VALID, TAKEN])

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == ['vehicle.names-unique [1].vehicleName', 'vehicle.names-unique vehicleName']
  }

  def 'a binding names the targets it renames, for each class of rule targets'() {
    given:
    def binding = only(new NameFree()).binding(VehicleFields).target('name', 'vehicleName').build()

    when:
    binding.check(TAKEN)

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.target()*.orElse('-') == ['vehicleName', 'vehicleName.first', '-']

    when:
    binding.check(new UpdateVehicle(1L, 'TR-1', 'taken'))

    then:
    e = thrown(BusinessRuleViolationException)
    e.violations()*.target()*.orElse('-') == ['vehicleName', 'vehicleName.first', '-']
  }

  def 'a binding renames targets in a check of a collection, after their position'() {
    given:
    def binding = only(new NameFree()).binding(VehicleFields).target('name', 'vehicleName').build()

    when:
    binding.checkAll([VALID, TAKEN])

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == [
      'vehicle.name-free [1].vehicleName',
      'vehicle.name-free [1].vehicleName.first',
      'vehicle.name-free [1]'
    ]
  }

  def 'a binding of a collection renames targets after their position'() {
    given:
    def binding = only(new NamesFree()).collectionBinding(VehicleFields).target('name', 'vehicleName').build()

    when:
    binding.check([TAKEN, VALID])

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == ['vehicle.names-free [0].vehicleName']
  }

  def 'a binding without names keeps the targets of the rules'() {
    when:
    only(new NameFree()).binding(VehicleFields).build().check(TAKEN)

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.target()*.orElse('-') == ['name', 'name.first', '-']
  }

  def 'the descriptions tell the names, in the order they were given'() {
    expect:
    muster.chain(VehicleFields, NameFree).withTarget('name', 'vehicleName').withTarget('code', 'vehicleCode').toString() ==
        "RuleChain[input=${VehicleFields.name}, mode=COLLECT_ALL, rules=[vehicle.name-free], targets={name=vehicleName, code=vehicleCode}]"
    only(new NameFree()).binding(CreateVehicle).rules(NameFree).target('name', 'vehicleName').build().toString() ==
        "Binding[name=${CreateVehicle.name}, input=${CreateVehicle.name}, mode=COLLECT_ALL, rules=[vehicle.name-free], excluded=[], targets={name=vehicleName}]"
    only().collectionBinding(CreateVehicle).target('name', 'vehicleName').build().toString() ==
        "CollectionBinding[name=${CreateVehicle.name}, elements=${CreateVehicle.name}, mode=COLLECT_ALL, rules=[], excluded=[], targets={name=vehicleName}]"
  }

  def 'a renamed target survives Java serialization as it is'() {
    when:
    muster.chain(VehicleFields, NameFree).withTarget('name', 'name.full').check(TAKEN)

    then:
    def e = thrown(BusinessRuleViolationException)
    Serialization.roundTrip(e).violations()*.target()*.orElse('-') == ['name.full', 'name.full.first', '-']
  }

  def 'a chain rejects a target it renames already, and a blank one'() {
    given:
    def chain = muster.chain(VehicleFields, NameFree).withTarget('name', 'vehicleName')

    when:
    chain.withTarget('name', 'title')

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "target 'name' is renamed to 'vehicleName' already"

    when:
    chain.withTarget(' ', 'title')

    then:
    e = thrown(IllegalArgumentException)
    e.message == 'from must not be blank'

    when:
    chain.withTarget('code', null)

    then:
    e = thrown(NullPointerException)
    e.message == 'to'
  }

  def 'the builders of bindings reject a target they rename already'() {
    when:
    muster.binding(VehicleFields).target('name', 'vehicleName').target('name', 'title')

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "target 'name' is renamed to 'vehicleName' already"

    when:
    muster.collectionBinding(VehicleFields).target('name', 'vehicleName').target('name', 'title')

    then:
    e = thrown(IllegalArgumentException)
    e.message == "target 'name' is renamed to 'vehicleName' already"
  }

  def 'threads checking with one chain that renames targets each get the renamed targets'() {
    given:
    def chain = muster.chain(VehicleFields, NameFree, NameAllowed).withTarget('name', 'vehicleName')
    def binding = only(new NameFree()).binding(VehicleFields).target('name', 'vehicleName').build()

    when:
    def targets = concurrently { n ->
      def vehicle = n % 2 == 0 ? TAKEN : new UpdateVehicle(n as Long, 'bad', 'taken')
      def single = []
      def all = []
      try {
        (n % 3 == 0 ? binding : chain).check(vehicle)
      } catch (BusinessRuleViolationException e) {
        single = e.violations()*.target()*.orElse('-')
      }
      try {
        // Elements of one class, as a binding requires.
        def valid = n % 2 == 0 ? VALID : new UpdateVehicle(0L, 'TR-1', 'Atlas')
        (n % 3 == 0 ? binding : chain).checkAll([valid, vehicle])
      } catch (BusinessRuleViolationException e) {
        all = e.violations()*.target()*.orElse('-')
      }
      [n % 3 == 0, single, all]
    }

    then:
    targets.every { byBinding, single, all ->
      single == (byBinding ? ['vehicleName', 'vehicleName.first', '-'] : ['vehicleName', 'vehicleName.first', '-', 'vehicleName']) &&
      all == (byBinding ? ['[1].vehicleName', '[1].vehicleName.first', '[1]'] : ['[1].vehicleName', '[1].vehicleName.first', '[1]', '[1].vehicleName'])
    }
  }
}
