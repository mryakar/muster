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

import java.util.concurrent.CompletableFuture
import me.yakar.muster.RuleValueSpec.Counted
import me.yakar.muster.RuleValueSpec.Dispatchable
import me.yakar.muster.RuleValueSpec.Driver
import me.yakar.muster.RuleValueSpec.Exists
import me.yakar.muster.RuleValueSpec.ExistsAtOnce
import me.yakar.muster.RuleValueSpec.Garage
import me.yakar.muster.RuleValueSpec.Owned
import me.yakar.muster.RuleValueSpec.OwnedAtOnce
import me.yakar.muster.RuleValueSpec.Renamable
import me.yakar.muster.RuleValueSpec.Small
import me.yakar.muster.RuleValueSpec.Vehicle
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.RequiresRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import me.yakar.muster.rule.RuleTarget
import me.yakar.muster.rule.ValueRule

/** The values that a check returns to its caller. */
class CheckedSpec extends EngineSpecification {

  /** A value rule that applies to its input only when told to. */
  @Rule(id = 'vehicle.sometimes')
  @RequiresRule([])
  static class Sometimes implements ValueRule<Long, String> {
    volatile boolean applies

    boolean appliesTo(RuleContext ctx) {
      applies
    }

    RuleResult check(Long input, RuleContext ctx) {
      RuleResult.passed('seen ' + input)
    }
  }

  @Rule(id = 'vehicle.after-sometimes')
  @RequiresRule(Sometimes)
  static class AfterSometimes implements ValueRule<Long, String> {
    RuleResult check(Long input, RuleContext ctx) {
      RuleResult.passed('after ' + ctx.valueOf(Sometimes))
    }
  }

  @Rule(id = 'fleet.sometimes-counted')
  @RequiresRule([])
  static class SometimesCounted implements ValueRule<List<Long>, Integer> {
    volatile boolean applies

    boolean appliesTo(RuleContext ctx) {
      applies
    }

    RuleResult check(List<Long> input, RuleContext ctx) {
      RuleResult.passed(input.size())
    }
  }

  static record Car(Long id) implements RuleTarget {}

  @Rule(id = 'car.found')
  @RequiresRule([])
  static class CarFound implements ValueRule<Car, Vehicle> {
    RuleResult check(Car input, RuleContext ctx) {
      RuleResult.passed(vehicle(input.id()))
    }
  }

  @Rule(id = 'car.parked')
  @RequiresRule([])
  static class CarParked implements ValueRule<Car, String> {
    RuleResult check(Car input, RuleContext ctx) {
      RuleResult.passed('spot ' + input.id())
    }
  }

  @Rule(id = 'car.plain')
  @RequiresRule([])
  static class CarPlain implements BusinessRule<Car> {
    RuleResult check(Car input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  def garage = new Garage()
  def sometimes = new Sometimes()
  def sometimesCounted = new SometimesCounted()

  Muster muster(Object... more) {
    Muster.builder()
        .rules(new Exists(garage), new Owned(), new Renamable(), new ExistsAtOnce(garage), new OwnedAtOnce(),
        new Driver(), new Dispatchable(), new Counted(), new Small(), sometimes, new AfterSometimes(),
        sometimesCounted, *more)
        .build()
  }

  static Vehicle vehicle(Long id) {
    RuleValueSpec.vehicle(id)
  }

  def 'a check returns the value a rule left, so that its caller does not read the data again'() {
    when:
    def checked = muster().chain(Long, Owned).check(7L)

    then:
    checked.valueOf(Exists) == vehicle(7L)
    checked.hasValue(Exists)
    garage.reads.get() == 1
  }

  def 'a check returns the values of every value rule it ran, listed or required through other rules'() {
    when:
    def checked = muster().chain(Long, Dispatchable).check(7L)

    then:
    checked.valueOf(Exists) == vehicle(7L)
    checked.valueOf(Driver) == 'driver of 7'
  }

  def 'a check whose rules leave no value returns one shared instance that holds none'() {
    given:
    def muster = muster(new CarPlain())

    when:
    def checked = muster.chain(Car, CarPlain).check(new Car(1L))

    then:
    checked.is(Checked.NONE)
    muster.chain(Car, CarPlain).checkAll([new Car(1L), new Car(2L)]).is(Checked.NONE)
    muster.check(new Car(1L)).is(Checked.NONE)
    checked.element(3).is(checked)
    checked.toString() == 'Checked[valueRules=[]]'

    when:
    checked.valueOf(Exists)

    then:
    def e = thrown(IllegalStateException)
    e.message == "no rule of the check leaves a value; ${Exists.typeName} is not a value rule of it"

    when:
    checked.hasValue(Exists)

    then:
    e = thrown(IllegalStateException)
    e.message == "no rule of the check leaves a value; ${Exists.typeName} is not a value rule of it"
  }

  def 'a check of a single input has no elements'() {
    when:
    muster().chain(Long, Owned).check(7L).element(0)

    then:
    def e = thrown(IllegalStateException)
    e.message == 'the check was of a single input, which has no elements; read its values with valueOf'
  }

  def 'the value of a rule that is not one of the check cannot be read'() {
    given:
    def checked = muster().chain(Long, Owned).check(7L)

    when:
    checked.valueOf(Driver)

    then:
    def e = thrown(IllegalStateException)
    e.message == "${Driver.typeName} is not a rule of the check: the check runs the rules it lists or binds by type and those they require, less the disabled and excluded ones"

    when:
    checked.hasValue(Driver)

    then:
    thrown(IllegalStateException)
  }

  def 'a disabled value rule is not one of the check'() {
    given:
    def muster = Muster.builder().rules(new Exists(garage), new Driver()).disable('vehicle.driver').build()

    when:
    muster.chain(Long, Exists, Driver).check(7L).valueOf(Driver)

    then:
    def e = thrown(IllegalStateException)
    e.message.startsWith("${Driver.typeName} is not a rule of the check")
  }

  def 'a value rule that a binding excludes is not one of the check'() {
    given:
    def binding = muster(new CarFound(), new CarParked()).binding(Car).exclude(CarFound).build()
    def checked = binding.check(new Car(7L))

    expect:
    checked.valueOf(CarParked) == 'spot 7'

    when:
    checked.valueOf(CarFound)

    then:
    def e = thrown(IllegalStateException)
    e.message.startsWith("${CarFound.typeName} is not a rule of the check")
  }

  def 'the class of a rule cannot be null'() {
    given:
    def checked = muster().chain(Long, Owned).check(7L)

    when:
    read.call(checked)

    then:
    def e = thrown(NullPointerException)
    e.message == 'rule'

    where:
    read << [
      { it.valueOf(null) },
      { it.hasValue(null) },
      { Checked.EMPTY.hasValue(null) }
    ]
  }

  def 'a rule of the check that leaves no value has none to read'() {
    when:
    muster().chain(Long, Owned).check(7L).valueOf((Class) Owned)

    then:
    def e = thrown(IllegalStateException)
    e.message == "rule 'vehicle.owned' of the check leaves no value; it is not a ValueRule"
  }

  def 'a value rule that does not apply to the input, or requires one that does not, leaves no value'() {
    given:
    def chain = muster().chain(Long, AfterSometimes)

    when:
    def checked = chain.check(7L)

    then:
    !checked.hasValue(Sometimes)
    !checked.hasValue(AfterSometimes)

    when:
    checked.valueOf(AfterSometimes)

    then:
    def e = thrown(IllegalStateException)
    e.message == "rule 'vehicle.after-sometimes' left no value, since it did not run: it, or a rule it requires, does not apply to the input; ask hasValue(AfterSometimes.class) first"

    when:
    sometimes.applies = true
    checked = chain.check(7L)

    then:
    checked.valueOf(Sometimes) == 'seen 7'
    checked.valueOf(AfterSometimes) == 'after seen 7'
  }

  def 'a failed check throws, whatever values the rules that passed left'() {
    when:
    muster().chain(Long, Owned).withMode(mode).check(12L)

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.ruleId() == ['vehicle.owned']

    where:
    mode << CheckMode.values()
  }

  def 'a check of a collection returns the value of each element, by its position: #how'() {
    when:
    def checked = muster().chain(Long, ruleClass).checkAll([7L, 9L, 3L])

    then:
    (0..2).collect {
      checked.element(it).valueOf(valueRule)
    } == [vehicle(7L), vehicle(9L), vehicle(3L)]
    checked.element(1).hasValue(valueRule)
    checked.toString() == "Checked[elements=3, valueRules=[${id}]]"
    checked.element(1).toString() == "Checked[element=1, valueRules=[${id}]]"

    where:
    how                  | ruleClass   | valueRule || id
    'checked one by one' | Owned       | Exists || 'vehicle.exists'
    'checked at once'    | OwnedAtOnce | ExistsAtOnce || 'vehicle.exists-at-once'
  }

  def 'the value of each element is read from the element'() {
    given:
    def checked = muster().chain(Long, Owned).checkAll([7L, 9L])

    when:
    checked.valueOf(Exists)

    then:
    def e = thrown(IllegalStateException)
    e.message == "rule 'vehicle.exists' leaves a value for each element; read it from the element, as in checked.element(i).valueOf(Exists.class)"

    when:
    checked.hasValue(Exists)

    then:
    thrown(IllegalStateException)
  }

  def 'the value of a rule of the whole collection is read from the check of the collection'() {
    given:
    def checked = muster().collectionBinding(Long).rules(Small).build().check([1L, 2L, 3L])

    expect:
    checked.valueOf(Counted) == 3

    when:
    checked.element(0).valueOf(Counted)

    then:
    def e = thrown(IllegalStateException)
    e.message == "rule 'fleet.counted' checks the whole collection; read its value from the check of the collection, not from an element"
  }

  def 'in a check of a collection, a value rule that does not apply leaves no value'() {
    when:
    def checked = muster().collectionBinding(Long).rules(SometimesCounted, AfterSometimes).build().check([1L, 2L])

    then:
    !checked.hasValue(SometimesCounted)
    !checked.element(0).hasValue(Sometimes)
    !checked.element(1).hasValue(AfterSometimes)
  }

  def 'the values of an element are read from the check of the collection, by a position in it'() {
    given:
    def checked = muster().chain(Long, Owned).checkAll([7L, 9L])

    when:
    checked.element(index)

    then:
    def e = thrown(IndexOutOfBoundsException)
    e.message == "the index ${index} is not the position of one of the 2 elements"

    where:
    index << [-1, 2]
  }

  def 'the values of an element have no elements'() {
    when:
    muster().chain(Long, Owned).checkAll([7L, 9L]).element(1).element(0)

    then:
    def e = thrown(IllegalStateException)
    e.message == 'these are the values of the element at [1] already; get the values of an element from the check of the collection'
  }

  def 'a check of an empty collection runs no rule and has no element: #how'() {
    when:
    def checked = check.call(muster(new CarFound()))

    then:
    checked.is(Checked.EMPTY)
    checked.toString() == 'Checked[elements=0, valueRules=[]]'

    when:
    checked.valueOf(Exists)

    then:
    def e = thrown(IllegalStateException)
    e.message == "the check was of an empty collection, which runs no rule, so ${Exists.typeName} left no value"

    !checked.hasValue(Exists)
    !checked.hasValue(Counted)

    when:
    checked.element(0)

    then:
    e = thrown(IndexOutOfBoundsException)
    e.message == 'the index 0 is not the position of one of the 0 elements'

    where:
    how                  | check
    'a chain'            | { Muster m -> m.chain(Long, Owned).checkAll([]) }
    'a binding'          | { Muster m -> m.binding(Long).rules(Owned).build().checkAll([]) }
    'a collection binding' | { Muster m ->
      m.collectionBinding(Long).rules(Small).build().check([])
    }
    'Muster'             | { Muster m -> m.checkAll([]) }
    'Muster, in a mode'  | { Muster m -> m.checkAll([], CheckMode.FAIL_FAST) }
  }

  def 'every way to check returns the values: #how'() {
    expect:
    read.call(check.call(muster(new CarFound()))) == vehicle(7L)

    where:
    how                                | check                                                                      | read
    'a chain'                          | { Muster m ->
      m.chain(Long, Owned).check(7L)
    }                             | {
      it.valueOf(Exists)
    }
    'a chain, in its own mode'         | { Muster m ->
      m.chain(Long, Owned).withMode(CheckMode.FAIL_FAST).check(7L)
    } | {
      it.valueOf(Exists)
    }
    'a chain, of a collection'         | { Muster m ->
      m.chain(Long, Owned).checkAll([7L])
    }                        | {
      it.element(0).valueOf(Exists)
    }
    'a binding'                        | { Muster m ->
      m.binding(Long).rules(Owned).build().check(7L)
    }             | {
      it.valueOf(Exists)
    }
    'a binding, of a collection'       | { Muster m ->
      m.binding(Long).rules(Owned).build().checkAll([3L, 7L])
    }    | {
      it.element(1).valueOf(Exists)
    }
    'a collection binding'             | { Muster m ->
      m.collectionBinding(Long).rules(Owned).build().check([7L])
    } | {
      it.element(0).valueOf(Exists)
    }
    'Muster'                           | { Muster m ->
      m.check(new Car(7L))
    }                                       | {
      it.valueOf(CarFound)
    }
    'Muster, in a mode'                | { Muster m ->
      m.check(new Car(7L), CheckMode.FAIL_FAST)
    }                  | {
      it.valueOf(CarFound)
    }
    'Muster, of a collection'          | { Muster m ->
      m.checkAll([new Car(3L), new Car(7L)])
    }                     | {
      it.element(1).valueOf(CarFound)
    }
    'Muster, of a collection, in a mode' | { Muster m ->
      m.checkAll([new Car(7L)], CheckMode.FAIL_FAST)
    }           | {
      it.element(0).valueOf(CarFound)
    }
  }

  def 'a check of a single input tells which value rules it has'() {
    expect:
    muster().chain(Long, Dispatchable).check(7L).toString() == 'Checked[valueRules=[vehicle.driver, vehicle.exists]]'
  }

  def 'each check of a chain from many threads at once returns the values of its own input'() {
    given:
    def chain = muster().chain(Long, Renamable)

    when:
    def mismatches = concurrently(32) { n ->
      (0..<200).findAll {
        def id = (n + it) % 9 + 1L
        chain.check(id).valueOf(Exists) != vehicle(id)
      }
    }

    then:
    mismatches.flatten().empty
    garage.reads.get() == 32 * 200
  }

  def 'each check of a collection from many threads at once returns the values of its own elements'() {
    given:
    def chain = muster().chain(Long, OwnedAtOnce)

    when:
    def mismatches = concurrently(16) { n ->
      def ids = [n * 2 + 1L, n * 2 + 3L, 5L]
      (0..<50).findAll {
        def checked = chain.checkAll(ids)
        (0..2).collect { checked.element(it).valueOf(ExistsAtOnce) } != ids.collect { vehicle(it) }
      }
    }

    then:
    mismatches.flatten().empty
  }

  def 'the values of a check may be read on another thread'() {
    given:
    def checked = muster().chain(Long, Owned).check(7L)

    expect:
    CompletableFuture.supplyAsync { checked.valueOf(Exists) }.get() == vehicle(7L)
  }
}
