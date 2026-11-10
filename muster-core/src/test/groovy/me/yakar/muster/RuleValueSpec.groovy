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

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.RequiresRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import me.yakar.muster.rule.ValueRule

/** Values that rules leave for the rules that require them, in single checks and checks of collections. */
class RuleValueSpec extends EngineSpecification {

  static record Vehicle(Long id, String owner) {}

  /** Vehicles 1 to 99 exist, owned by amo except the even ones above 10, which ece owns; counts its reads. */
  static class Garage {
    final AtomicInteger reads = new AtomicInteger()

    Optional<Vehicle> find(Long id) {
      reads.incrementAndGet()
      vehicle(id)
    }

    Map<Long, Vehicle> findAll(Collection<Long> ids) {
      reads.incrementAndGet()
      ids.collectEntries { [it, vehicle(it).orElse(null)] }.findAll { it.value != null }
    }

    private static Optional<Vehicle> vehicle(Long id) {
      id in 1L..99L ? Optional.of(new Vehicle(id, id> 10 && id % 2 == 0 ? 'ece' : 'amo')) : Optional.empty()
    }
  }

  @Rule(id = 'vehicle.exists')
  @RequiresRule([])
  static class Exists implements ValueRule<Long, Vehicle> {
    final Garage garage

    Exists(Garage garage) {
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

  @Rule(id = 'vehicle.owned')
  @RequiresRule(Exists)
  static class Owned implements BusinessRule<Long> {
    final List<Vehicle> seen = new CopyOnWriteArrayList<>()

    RuleResult check(Long input, RuleContext ctx) {
      def vehicle = ctx.valueOf(Exists)
      seen << vehicle
      vehicle.owner() == 'amo' ? RuleResult.passed() : RuleResult.failed(ctx.violation().build())
    }
  }

  @Rule(id = 'vehicle.renamable')
  @RequiresRule(Owned)
  static class Renamable implements BusinessRule<Long> {
    final List<Vehicle> seen = new CopyOnWriteArrayList<>()
    final List<List<Long>> inputsAndValues = new CopyOnWriteArrayList<>()

    RuleResult check(Long input, RuleContext ctx) {
      def vehicle = ctx.valueOf(Exists)
      seen << vehicle
      inputsAndValues << [input, vehicle.id()]
      RuleResult.passed()
    }
  }

  @Rule(id = 'vehicle.driver')
  @RequiresRule([])
  static class Driver implements ValueRule<Long, String> {
    RuleResult check(Long input, RuleContext ctx) {
      RuleResult.passed('driver of ' + input)
    }
  }

  @Rule(id = 'vehicle.dispatchable')
  @RequiresRule([Driver, Owned])
  static class Dispatchable implements BusinessRule<Long> {
    final List<List<Object>> seen = new CopyOnWriteArrayList<>()

    RuleResult check(Long input, RuleContext ctx) {
      seen << [ctx.valueOf(Exists), ctx.valueOf(Driver)]
      RuleResult.passed()
    }
  }

  @Rule(id = 'vehicle.unrelated')
  @RequiresRule([])
  static class Unrelated implements BusinessRule<Long> {
    RuleResult check(Long input, RuleContext ctx) {
      ctx.valueOf(Exists)
      RuleResult.passed()
    }
  }

  @Rule(id = 'vehicle.reads-another')
  @RequiresRule(Exists)
  static class ReadsAnother implements BusinessRule<Long> {
    RuleResult check(Long input, RuleContext ctx) {
      ctx.valueOf(ExistsAtOnce)
      RuleResult.passed()
    }
  }

  @Rule(id = 'vehicle.exists-at-once')
  @RequiresRule([])
  static class ExistsAtOnce implements ValueRule<Long, Vehicle> {
    final Garage garage

    ExistsAtOnce(Garage garage) {
      this.garage = garage
    }

    RuleResult check(Long input, RuleContext ctx) {
      throw new AssertionError('checked one by one')
    }

    RuleResult checkAll(List<? extends Long> inputs, RuleContext ctx) {
      def found = garage.findAll(inputs)
      RuleResult.ofElements((0..<inputs.size()).collect { i ->
        def vehicle = found[inputs[i]]
        vehicle != null ? RuleResult.passed(vehicle) : RuleResult.failed(ctx.element(i).violation().build())
      })
    }
  }

  @Rule(id = 'vehicle.owned-at-once')
  @RequiresRule(ExistsAtOnce)
  static class OwnedAtOnce implements BusinessRule<Long> {
    final List<Vehicle> seen = new CopyOnWriteArrayList<>()
    final List<List<Long>> inputsAndValues = new CopyOnWriteArrayList<>()

    RuleResult check(Long input, RuleContext ctx) {
      throw new AssertionError('checked one by one')
    }

    RuleResult checkAll(List<? extends Long> inputs, RuleContext ctx) {
      def violations = []
      inputs.eachWithIndex { input, i ->
        def vehicle = ctx.element(i).valueOf(ExistsAtOnce)
        seen << vehicle
        inputsAndValues << [input, vehicle.id()]
        if (vehicle.owner() != 'amo') {
          violations << ctx.element(i).violation().build()
        }
      }
      violations ? RuleResult.failed(violations) : RuleResult.passed()
    }
  }

  @Rule(id = 'vehicle.read-from-the-collection')
  @RequiresRule(ExistsAtOnce)
  static class ReadsFromTheCollection implements BusinessRule<Long> {
    RuleResult check(Long input, RuleContext ctx) {
      throw new AssertionError('checked one by one')
    }

    RuleResult checkAll(List<? extends Long> inputs, RuleContext ctx) {
      ctx.valueOf(ExistsAtOnce)
      RuleResult.passed()
    }
  }

  /** A rule of the whole fleet: at most two of its vehicles may be ece's. */
  @Rule(id = 'fleet.mostly-amo')
  @RequiresRule(Exists)
  static class MostlyAmo implements BusinessRule<List<Long>> {
    RuleResult check(List<Long> input, RuleContext ctx) {
      def owners = (0..<input.size()).collect { ctx.element(it).valueOf(Exists).owner() }
      owners.count('ece') > 2 ? RuleResult.failed(ctx.violation().clientArg('owners', owners).build()) : RuleResult.passed()
    }
  }

  @Rule(id = 'fleet.counted')
  @RequiresRule([])
  static class Counted implements ValueRule<List<Long>, Integer> {
    RuleResult check(List<Long> input, RuleContext ctx) {
      RuleResult.passed(input.size())
    }
  }

  @Rule(id = 'fleet.small')
  @RequiresRule(Counted)
  static class Small implements BusinessRule<List<Long>> {
    RuleResult check(List<Long> input, RuleContext ctx) {
      // A value of the whole collection reads the same from the context of an element.
      assert ctx.element(0).valueOf(Counted) == ctx.valueOf(Counted)
      ctx.valueOf(Counted) <= 3 ? RuleResult.passed() : RuleResult.failed(ctx.violation().build())
    }
  }

  /** Returns what its closure returns, for rules that misbehave. */
  @Rule(id = 'rule.value')
  @RequiresRule([])
  static class OddValue implements ValueRule<Long, Vehicle> {
    Closure<RuleResult> result

    RuleResult check(Long input, RuleContext ctx) {
      result(input, ctx)
    }
  }

  @Rule(id = 'rule.plain')
  @RequiresRule([])
  static class OddPlain implements BusinessRule<Long> {
    Closure<RuleResult> result

    RuleResult check(Long input, RuleContext ctx) {
      result(input, ctx)
    }
  }

  @Rule(id = 'rule.value-at-once')
  @RequiresRule([])
  static class OddValueAtOnce implements ValueRule<Long, Vehicle> {
    Closure<RuleResult> result

    RuleResult check(Long input, RuleContext ctx) {
      throw new AssertionError('checked one by one')
    }

    RuleResult checkAll(List<? extends Long> inputs, RuleContext ctx) {
      result(inputs, ctx)
    }
  }

  @Rule(id = 'rule.plain-at-once')
  @RequiresRule([])
  static class OddPlainAtOnce implements BusinessRule<Long> {
    Closure<RuleResult> result

    RuleResult check(Long input, RuleContext ctx) {
      throw new AssertionError('checked one by one')
    }

    RuleResult checkAll(List<? extends Long> inputs, RuleContext ctx) {
      result(inputs, ctx)
    }
  }

  @Rule(id = 'rule.whole')
  @RequiresRule([])
  static class OddWhole implements BusinessRule<List<Long>> {
    Closure<RuleResult> result

    RuleResult check(List<Long> input, RuleContext ctx) {
      result(input, ctx)
    }
  }

  /** Notes the inputs it checks, after the rule that checks them at once. */
  @Rule(id = 'rule.after')
  @RequiresRule(OddPlainAtOnce)
  static class After implements BusinessRule<Long> {
    final List<Long> checked = new CopyOnWriteArrayList<>()

    RuleResult check(Long input, RuleContext ctx) {
      checked << input
      RuleResult.passed()
    }
  }

  def garage = new Garage()
  def owned = new Owned()
  def renamable = new Renamable()

  Muster muster(Object... more) {
    Muster.builder()
        .rules(new Exists(garage), owned, renamable, new Unrelated(), new ExistsAtOnce(garage), *more)
        .mode(CheckMode.COLLECT_ALL)
        .build()
  }

  static Vehicle vehicle(Long id) {
    Garage.vehicle(id).orElseThrow()
  }

  def 'a rule reads the value of a rule it requires, which read the data once'() {
    when:
    muster().chain(Long, Owned).check(7L)

    then:
    owned.seen == [vehicle(7L)]
    garage.reads.get() == 1
  }

  def 'a rule reads the value of a rule it requires through another rule'() {
    when:
    muster().chain(Long, Renamable).check(7L)

    then:
    renamable.seen == [vehicle(7L)]
    garage.reads.get() == 1
  }

  def 'a rule reads the values of every value rule it requires, directly and through other rules'() {
    given:
    def dispatchable = new Dispatchable()

    when:
    muster(new Driver(), dispatchable).chain(Long, Dispatchable).check(7L)

    then:
    dispatchable.seen == [[vehicle(7L), 'driver of 7']]
  }

  def 'a rule that requires a value rule that failed is skipped, as any rule that requires a failed rule'() {
    when:
    muster().chain(Long, Owned).check(404L)

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.ruleId() == ['vehicle.exists']
    owned.seen.empty
  }

  def 'a chain with a value rule that no rule reads checks as any other'() {
    when:
    muster().chain(Long, Exists).check(7L)

    then:
    garage.reads.get() == 1
  }

  def 'a rule cannot read the value of a rule it does not require'() {
    when:
    muster().chain(Long, Exists, Unrelated).check(7L)

    then:
    def e = thrown(IllegalStateException)
    e.message == "rule 'vehicle.unrelated' has no value of rule 'vehicle.exists': add Exists to its @RequiresRule, or to that of a rule it requires; in a test, give the value with RuleContext.Builder.value"
  }

  def 'a rule cannot read the value of a rule it does not require, also when it requires another value rule'() {
    when:
    muster(new ReadsAnother()).chain(Long, ReadsAnother).check(7L)

    then:
    def e = thrown(IllegalStateException)
    e.message == "rule 'vehicle.reads-another' reads the value of rule 'vehicle.exists-at-once', which it does not require; add ExistsAtOnce to its @RequiresRule, or to that of a rule it requires"
  }

  def 'the result of a single check must fit the rule: #problem'() {
    given:
    def odd = new OddValue(result: result)
    def plain = new OddPlain(result: result)

    when:
    muster(odd, plain).chain(Long, ruleClass).check(7L)

    then:
    def e = thrown(IllegalStateException)
    e.message == message

    where:
    problem                       | ruleClass | result || message
    'a value rule with no value'  | OddValue  | { i, c ->
      RuleResult.passed()
    } || "value rule 'rule.value' passed without a value; pass with RuleResult.passed(value)"
    'a value of another type'     | OddValue  | { i, c ->
      RuleResult.passed('Atlas')
    } || "value rule 'rule.value' left a java.lang.String, not a ${Vehicle.typeName}"
    'a value of a plain rule'     | OddPlain  | { i, c ->
      RuleResult.passed(vehicle(7L))
    } || "rule 'rule.plain' passed with a value, but is not a value rule; implement ValueRule<Input, Value> to leave a value"
    'the result of a collection'  | OddPlain  | { i, c ->
      RuleResult.ofElements([RuleResult.passed()])
    } || "rule 'rule.plain' returned the result of a collection; RuleResult.ofElements is for checkAll"
  }

  def 'each check of a chain from many threads at once reads its own values'() {
    given:
    def chain = muster().chain(Long, Renamable)

    when:
    concurrently(32) { n ->
      200.times { chain.check(n % 5 + 1L) }
    }

    then: 'every check read the vehicle of its own input, once'
    renamable.inputsAndValues.size() == 32 * 200
    renamable.inputsAndValues.every { it[0] == it[1] }
    garage.reads.get() == 32 * 200
  }

  def 'each check of a collection from many threads at once reads the values of its own elements'() {
    given:
    def ownedAtOnce = new OwnedAtOnce()
    def chain = muster(ownedAtOnce).chain(Long, OwnedAtOnce)

    when: 'each thread checks vehicles of its own, with one that does not exist among them'
    def failures = concurrently(16) { n ->
      (0..<50).collect {
        try {
          chain.checkAll([n * 2 + 1L, 404L, n * 2 + 3L])
          null
        } catch (BusinessRuleViolationException e) {
          described(e)
        }
      }
    }

    then: 'every check failed only for the missing vehicle, and read its own two vehicles'
    failures.flatten().unique() == ['vehicle.exists-at-once [1]']
    ownedAtOnce.seen.size() == 16 * 50 * 2
    ownedAtOnce.inputsAndValues.every { it[0] == it[1] }
    ownedAtOnce.seen*.id().toSet() == (0..<16).collectMany { [it * 2 + 1L, it * 2 + 3L] }.toSet()
  }

  def 'in a check of a collection, a rule reads the value of each element, checked one by one'() {
    when:
    muster().chain(Long, Renamable).checkAll([7L, 12L, 404L, 9L])

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == ['vehicle.exists [2]', 'vehicle.owned [1]']
    owned.seen == [vehicle(7L), vehicle(12L), vehicle(9L)]
    renamable.seen == [vehicle(7L), vehicle(9L)]
    garage.reads.get() == 4
  }

  def 'a value rule that checks at once leaves the value of each element it passed, and the rules that require it read them'() {
    given:
    def ownedAtOnce = new OwnedAtOnce()

    when:
    muster(ownedAtOnce).chain(Long, OwnedAtOnce).checkAll([7L, 404L, 12L, 9L])

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == ['vehicle.exists-at-once [1]', 'vehicle.owned-at-once [2]']
    ownedAtOnce.seen == [vehicle(7L), vehicle(12L), vehicle(9L)]
    garage.reads.get() == 1
  }

  def 'the value of each element is read from the context of an element'() {
    when:
    muster(new ReadsFromTheCollection()).chain(Long, ReadsFromTheCollection).checkAll([7L])

    then:
    def e = thrown(IllegalStateException)
    e.message == "rule 'vehicle.exists-at-once' leaves a value for each element; read it from the context of an element, as in ctx.element(i).valueOf(ExistsAtOnce.class)"
  }

  def 'a rule of the whole collection reads the value of each element from the context of the element'() {
    given:
    def binding = muster(new MostlyAmo()).collectionBinding(Long).rules(MostlyAmo).build()

    when:
    binding.check([12L, 7L, 14L, 16L])

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.clientArgs() == [[owners: ['ece', 'amo', 'ece', 'ece']]]
    garage.reads.get() == 4

    when:
    binding.check([12L, 7L, 14L])

    then:
    notThrown(BusinessRuleViolationException)
  }

  def 'a rule of the whole collection leaves a value for the rules of the whole collection that require it'() {
    given:
    def binding = muster(new Counted(), new Small()).collectionBinding(Long).rules(Small).build()

    when:
    binding.check([1L, 2L, 3L, 4L])

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.ruleId() == ['fleet.small']

    when:
    binding.check([1L, 2L, 3L])

    then:
    notThrown(BusinessRuleViolationException)
  }

  def 'the result of a rule that checks at once must fit the rule: #problem'() {
    given:
    def odd = new OddValueAtOnce(result: result)
    def plain = new OddPlainAtOnce(result: result)

    when:
    muster(odd, plain).chain(Long, ruleClass).checkAll([7L, 8L])

    then:
    def e = thrown(IllegalStateException)
    e.message == message

    where:
    problem                                 | ruleClass      | result || message
    'a value rule that passed with no value' | OddValueAtOnce | { i, c ->
      RuleResult.passed()
    } || "value rule 'rule.value-at-once' checked a collection at once without the value of each element it passed; return the result of each element with RuleResult.ofElements"
    'a value rule that passed with one value' | OddValueAtOnce | { i, c ->
      RuleResult.passed(vehicle(7L))
    } || "value rule 'rule.value-at-once' checked a collection at once without the value of each element it passed; return the result of each element with RuleResult.ofElements"
    'a value rule that failed some elements' | OddValueAtOnce | { i, c ->
      RuleResult.failed(c.element(0).violation().build())
    } || "value rule 'rule.value-at-once' checked a collection at once without the value of each element it passed; return the result of each element with RuleResult.ofElements"
    'a plain rule that passed with a value'  | OddPlainAtOnce | { i, c ->
      RuleResult.passed(vehicle(7L))
    } || "rule 'rule.plain-at-once' passed with a value, but is not a value rule; implement ValueRule<Input, Value> to leave a value"
    'too few results'                        | OddValueAtOnce | { i, c ->
      RuleResult.ofElements([RuleResult.passed(vehicle(7L))])
    } || "rule 'rule.value-at-once' returned 1 results for 2 inputs; return the result of each input it received"
    'a violation of another element'         | OddValueAtOnce | { i, c ->
      RuleResult.ofElements([RuleResult.failed(c.element(1).violation().build()), RuleResult.passed(vehicle(8L))])
    } || "rule 'rule.value-at-once' gave the input at index 0 a violation that is not about it; build it from ctx.element(0)"
    'a violation of the whole collection'    | OddValueAtOnce | { i, c ->
      RuleResult.ofElements([RuleResult.passed(vehicle(7L)), RuleResult.failed(c.violation().build())])
    } || "rule 'rule.value-at-once' gave the input at index 1 a violation that is not about it; build it from ctx.element(1)"
    'an element value of another type'       | OddValueAtOnce | { i, c ->
      RuleResult.ofElements([RuleResult.passed(vehicle(7L)), RuleResult.passed('Zeus')])
    } || "value rule 'rule.value-at-once' left a java.lang.String for the element at [1], not a ${Vehicle.typeName}"
    'an element value of a plain rule'       | OddPlainAtOnce | { i, c ->
      RuleResult.ofElements([RuleResult.passed(vehicle(7L)), RuleResult.passed()])
    } || "rule 'rule.plain-at-once' passed with a value for the element at [0], but is not a value rule; implement ValueRule<Input, Value> to leave a value"
  }

  def 'a value rule that checks at once may fail every element without values: #how'() {
    given:
    def odd = new OddValueAtOnce(result: result)

    when:
    muster(odd).chain(Long, OddValueAtOnce).checkAll([7L, 8L])

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == violations

    where:
    how                 | result || violations
    'each element'      | { i, c ->
      RuleResult.failed(c.element(0).violation().build(), c.element(1).violation().build())
    } || ['rule.value-at-once [0]', 'rule.value-at-once [1]']
    'the whole of them' | { i, c ->
      RuleResult.failed(c.violation().build())
    } || ['rule.value-at-once -']
  }

  def 'a value rule that checks at once may fail the whole collection after a rule that passed it'() {
    given: 'neither rule is required, so Muster keeps no marks of either'
    def plain = new OddPlainAtOnce(result: { i, c -> RuleResult.passed() })
    def odd = new OddValueAtOnce(result: { i, c -> RuleResult.failed(c.violation().build()) })

    when:
    muster(plain, odd).chain(Long, OddPlainAtOnce, OddValueAtOnce).checkAll([7L, 8L])

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == ['rule.value-at-once -']
  }

  def 'a value rule that checks at once may fail the whole collection after a value rule that passed it'() {
    given: 'neither rule is required, so Muster keeps no marks of either'
    def odd = new OddValueAtOnce(result: { i, c -> RuleResult.failed(c.violation().build()) })

    when:
    muster(odd).chain(Long, ExistsAtOnce, OddValueAtOnce).checkAll([7L, 8L])

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == ['rule.value-at-once -']
  }

  def 'a rule that leaves no value may give the result of each element too'() {
    given:
    def after = new After()
    def plain = new OddPlainAtOnce(result: { i, c ->
      RuleResult.ofElements([RuleResult.passed(), RuleResult.failed(c.element(1).violation().build()), RuleResult.passed()])
    })

    when:
    muster(plain, after).chain(Long, After).checkAll([7L, 8L, 9L])

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == ['rule.plain-at-once [1]']
    after.checked == [7L, 9L]
  }

  def 'a rule that leaves no value may give the result of each element, all passed'() {
    given:
    def after = new After()
    def plain = new OddPlainAtOnce(result: { i, c ->
      RuleResult.ofElements([RuleResult.passed(), RuleResult.passed()])
    })

    when:
    muster(plain, after).chain(Long, After).checkAll([7L, 8L])

    then:
    after.checked == [7L, 8L]
  }

  def 'a rule checked one by one returns the result of its element: #problem'() {
    given:
    def odd = new OddValue(result: result)

    when:
    muster(odd).chain(Long, OddValue).checkAll([7L, 8L])

    then:
    def e = thrown(IllegalStateException)
    e.message == message

    where:
    problem                      | result || message
    'the result of a collection' | { i, c ->
      RuleResult.ofElements([RuleResult.passed()])
    } || "rule 'rule.value' returned the result of a collection for the element at [0]; RuleResult.ofElements is for checkAll"
    'no value'                   | { i, c ->
      RuleResult.passed()
    } || "value rule 'rule.value' passed without a value for the element at [0]; pass with RuleResult.passed(value)"
  }

  def 'a rule of the whole collection returns one result: #problem'() {
    given:
    def odd = new OddWhole(result: result)

    when:
    muster(odd).collectionBinding(Long).rules(OddWhole).build().check([7L, 8L])

    then:
    def e = thrown(IllegalStateException)
    e.message == message

    where:
    problem                      | result || message
    'the result of a collection' | { i, c ->
      RuleResult.ofElements([RuleResult.passed()])
    } || "rule 'rule.whole' returned the result of a collection; RuleResult.ofElements is for checkAll"
    'a value of a plain rule'    | { i, c ->
      RuleResult.passed(3)
    } || "rule 'rule.whole' passed with a value, but is not a value rule; implement ValueRule<Input, Value> to leave a value"
  }
}
