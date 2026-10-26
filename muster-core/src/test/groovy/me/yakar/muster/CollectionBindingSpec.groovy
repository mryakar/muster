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
import java.util.logging.Level
import me.yakar.muster.EngineSpecification.VehicleFields
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.RequiresRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult

/** Checking a collection with rules of its elements and rules of the whole collection. */
class CollectionBindingSpec extends EngineSpecification {

  static final String CREATE = CreateVehicle.typeName
  static final String FIELDS = VehicleFields.typeName

  /** At most two vehicles in one call. */
  @Rule(id = 'vehicle.batch-limit')
  static class BatchLimit implements BusinessRule<Collection<? extends VehicleFields>> {
    final List<List<?>> received = new CopyOnWriteArrayList<>()

    RuleResult check(Collection<? extends VehicleFields> input, RuleContext ctx) {
      received << input
      input.size() > 2 ? RuleResult.failed(ctx.violation().clientArg('limit', 2).build()) : RuleResult.passed()
    }
  }

  /** No two vehicles of one call share a name; checked once the batch is within its limit. */
  @Rule(id = 'vehicle.names-distinct')
  @RequiresRule(BatchLimit)
  static class NamesDistinct implements BusinessRule<List<VehicleFields>> {
    RuleResult check(List<VehicleFields> input, RuleContext ctx) {
      input*.name().toSet().size() <input.size() ? RuleResult.failed(ctx.violation().build()) : RuleResult.passed()
    }
  }

  @Rule(id = 'vehicle.distinct-set')
  static class SetOfVehicles implements BusinessRule<Set<VehicleFields>> {
    RuleResult check(Set<VehicleFields> input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  @Rule(id = 'task.limit')
  static class TaskLimit implements BusinessRule<List<Long>> {
    final List<List<Long>> received = new CopyOnWriteArrayList<>()

    RuleResult check(List<Long> input, RuleContext ctx) {
      received << List.copyOf(input)
      RuleResult.passed()
    }
  }

  /** Fails 404, whatever it is given. */
  @Rule(id = 'any.not-found')
  static class NotFound implements BusinessRule<Object> {
    RuleResult check(Object input, RuleContext ctx) {
      input == 404L ? RuleResult.failed(ctx.violation().build()) : RuleResult.passed()
    }
  }

  /** A limit of tasks that requires every task to be found. */
  @Rule(id = 'task.limit')
  @RequiresRule(NotFound)
  static class TaskLimitOfFound extends TaskLimit {}

  @Rule(id = 'vehicle.batch-limit')
  static class NeverApplies implements BusinessRule<List<VehicleFields>> {
    boolean appliesTo(RuleContext ctx) {
      false
    }

    RuleResult check(List<VehicleFields> input, RuleContext ctx) {
      RuleResult.failed(ctx.violation().build())
    }
  }

  @Rule(id = 'vehicle.names-distinct')
  @RequiresRule(NeverApplies)
  static class DistinctAfterNeverApplies extends NamesDistinct {}

  @Rule(id = 'vehicle.batch-limit')
  static class ReturnsNull implements BusinessRule<List<VehicleFields>> {
    RuleResult check(List<VehicleFields> input, RuleContext ctx) {
      null
    }
  }

  static final List<CreateVehicle> THREE = [
    new CreateVehicle('TR-1', 'Atlas'),
    new CreateVehicle('bad', 'Atlas'),
    new CreateVehicle('TR-3', 'Zeus')
  ]

  Muster vehicles(BusinessRule<?>... rules) {
    Muster.builder().rules(rules).mode(CheckMode.COLLECT_ALL).build()
  }

  static List<String> described(BusinessRuleViolationException e) {
    e.violations().collect { it.ruleId() + ' ' + it.target().orElse('-') }
  }

  /** All tasks positive; a rule of the whole collection. */
  @Rule(id = 'task.positive')
  static class TasksPositive implements BusinessRule<List<Long>> {
    RuleResult check(List<Long> input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static abstract class AnyRule<X> implements BusinessRule<X> {}

  static abstract class ListRule<E> extends AnyRule<List<E>> {}

  /** Sums the tasks once they are positive; its element type comes through two generic levels. */
  @Rule(id = 'task.sum')
  @RequiresRule(TasksPositive)
  static class TaskSum extends ListRule<Long> {
    final List<List<Long>> received = new CopyOnWriteArrayList<>()

    RuleResult check(List<Long> input, RuleContext ctx) {
      received << List.copyOf(input)
      RuleResult.passed()
    }
  }

  def 'a rule whose element type comes through two generic levels checks collections of that type only'() {
    given:
    def sum = new TaskSum()
    def muster = vehicles(new TasksPositive(), sum, new CodeFormat())

    when:
    muster.checkAll([VALID])
    muster.collectionBinding(Long).build().check([1L, 2L])

    then:
    sum.received.empty

    when:
    muster.collectionBinding(Long).rules(TaskSum).build().check([1L, 2L])

    then:
    sum.received == [[1L, 2L]]

    when:
    muster.collectionBinding(CreateVehicle).rules(TaskSum).build()

    then:
    thrown(IllegalArgumentException)
  }

  /** An amount must not be negative, whatever kind of number it is. */
  @Rule(id = 'amount.positive')
  static class AmountPositive implements BusinessRule<Number> {
    RuleResult check(Number input, RuleContext ctx) {
      input.doubleValue() < 0 ? RuleResult.failed(ctx.violation().build()) : RuleResult.passed()
    }
  }

  /** At most two amounts in one call. */
  @Rule(id = 'amount.limit')
  static class AmountLimit implements BusinessRule<List<Number>> {
    RuleResult check(List<Number> input, RuleContext ctx) {
      input.size() > 2 ? RuleResult.failed(ctx.violation().build()) : RuleResult.passed()
    }
  }

  /** Counts its checks; a rule of each vehicle. */
  @Rule(id = 'vehicle.counted')
  static class CountedVehicle implements BusinessRule<VehicleFields> {
    final List<Object> checked = new CopyOnWriteArrayList<>()

    RuleResult check(VehicleFields input, RuleContext ctx) {
      checked << input
      RuleResult.passed()
    }
  }

  /** Checks anything that can be iterated: neither a vehicle nor, as a rule of the whole, a list of them. */
  @Rule(id = 'any.iterable')
  static class AnyIterable implements BusinessRule<Iterable<?>> {
    RuleResult check(Iterable<?> input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  @Rule(id = 'vehicle.iterated')
  @RequiresRule(AnyIterable)
  static class Iterated implements BusinessRule<List<VehicleFields>> {
    RuleResult check(List<VehicleFields> input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static String cannotCheckIterable(String label) {
    "the chain for $label cannot check rule 'any.iterable', which checks java.lang.Iterable: neither $CREATE nor a list of $CREATE is one; a rule of the whole collection checks a list, so what it requires checks lists or each element".toString()
  }

  def 'a rule of the whole collection that requires a rule able to check neither stops a binding when it is built: #type.simpleName'() {
    when:
    vehicles(new AnyIterable(), new Iterated()).collectionBinding(type).rules(Iterated).build()

    then:
    def e = thrown(IllegalArgumentException)
    e.message == message.replace(CREATE, type.typeName)

    where:
    type || message
    CreateVehicle || cannotCheckIterable("a collection of ${CREATE}")
    VehicleFields || cannotCheckIterable("a collection of ${CREATE}")
  }

  def 'such a rule bound by type stops the first check of a collection'() {
    when:
    vehicles(new AnyIterable(), new Iterated()).checkAll([VALID])

    then:
    def e = thrown(IllegalArgumentException)
    e.message == cannotCheckIterable("a collection of ${CREATE}")
  }

  def 'listed rules of the elements and of the whole collection run in the order they are listed'() {
    given:
    def binding = vehicles(new CodeFormat(), new BatchLimit()).collectionBinding(CreateVehicle).rules(BatchLimit, CodeFormat).build()

    when:
    binding.check(THREE)

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == ['vehicle.batch-limit -', 'vehicle.code-format [1]']
  }

  def 'a rule of the whole collection gets a copy of it as a list'() {
    given:
    def limit = new BatchLimit()
    def set = new LinkedHashSet([VALID])

    when:
    vehicles(limit).collectionBinding(CreateVehicle).rules(BatchLimit).build().check(set)

    then:
    limit.received == [[VALID]]
    limit.received[0] instanceof List
  }

  def 'rules bound to the class of rule targets, of both kinds, run in the order of their ids'() {
    given:
    def binding = vehicles(new NamesDistinct(), new NameUnique(), new CodeFormat(), new BatchLimit()).collectionBinding(VehicleFields).build()
    def vehicles = [new CreateVehicle('bad', 'taken'), new CreateVehicle('TR-2', 'taken')]

    when:
    binding.check(vehicles)

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == [
      'vehicle.code-format [0]',
      'vehicle.name-unique [0]',
      'vehicle.name-unique [1]',
      'vehicle.names-distinct -'
    ]
  }

  def 'a rule of the whole collection that requires another runs only if that one passed'() {
    given:
    def binding = vehicles(new NamesDistinct(), new BatchLimit()).collectionBinding(CreateVehicle).rules(NamesDistinct).build()

    when:
    binding.check(THREE)

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == ['vehicle.batch-limit -']
  }

  def 'a rule of the whole collection checks it only once every element passed what it requires'() {
    given:
    def limit = new TaskLimitOfFound()
    def binding = vehicles(new NotFound(), limit).collectionBinding(Long).rules(TaskLimitOfFound).build()

    when:
    binding.check([1L, 404L])

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == ['any.not-found [1]']
    limit.received.empty

    when:
    binding.check([1L, 2L])

    then:
    limit.received == [[1L, 2L]]
  }

  def 'in fail-fast mode, a violated rule of the whole collection stops the check'() {
    given:
    def binding = vehicles(new CodeFormat(), new BatchLimit()).collectionBinding(CreateVehicle).rules(BatchLimit, CodeFormat).mode(CheckMode.FAIL_FAST).build()

    when:
    binding.check(THREE)

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == ['vehicle.batch-limit -']
  }

  def 'a rule of the whole collection that does not apply is skipped, and so are the rules that require it'() {
    given:
    def binding = vehicles(new NeverApplies(), new DistinctAfterNeverApplies()).collectionBinding(CreateVehicle).rules(DistinctAfterNeverApplies).build()

    expect:
    binding.check(THREE)
  }

  def 'a rule of the whole collection that returns null fails the check'() {
    when:
    vehicles(new ReturnsNull()).collectionBinding(CreateVehicle).rules(ReturnsNull).build().check([VALID])

    then:
    def e = thrown(NullPointerException)
    e.message == "rule 'vehicle.batch-limit' returned null"
  }

  def 'a listed rule must check the elements or whole collections of them: #ruleClass.simpleName'() {
    when:
    vehicles(rule).collectionBinding(CreateVehicle).rules(ruleClass).build()

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "${ruleClass.typeName} checks $checks, which neither $CREATE nor a list of $CREATE is; a rule of the whole collection takes a List, SequencedCollection or Collection of its elements"

    where:
    rule                  | ruleClass || checks
    new SetOfVehicles()   | SetOfVehicles || "java.util.Set of ${FIELDS}"
    new TaskLimit()       | TaskLimit || 'java.util.List of java.lang.Long'
    new VehicleExists()   | VehicleExists || 'java.lang.Long'
  }

  def 'a binding is checked as Binding checks its listed and excluded rules'() {
    when:
    vehicles(new BatchLimit()).collectionBinding(CreateVehicle).rules(BatchLimit).exclude(BatchLimit).build()

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "${BatchLimit.typeName} is both listed and excluded"
  }

  def 'an excluded rule of the whole collection is left out, and so is a rule bound by type that requires it'() {
    given:
    def binding = vehicles(new NamesDistinct(), new BatchLimit(), new CodeFormat()).collectionBinding(VehicleFields).exclude(BatchLimit).build()
    log.level = Level.FINE

    when:
    binding.check(THREE)

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == ['vehicle.code-format [1]']
    logged*.message.contains("The chain for ${FIELDS} (a collection of ${CREATE}) leaves out rules that require excluded rules: 'vehicle.names-distinct' requires 'vehicle.batch-limit'".toString())
  }

  def 'a disabled rule of the whole collection does not bind'() {
    given:
    def muster = Muster.builder().rules(new BatchLimit()).disable('vehicle.batch-limit').build()

    expect:
    muster.collectionBinding(VehicleFields).build().check(THREE)
  }

  def 'a binding of a final element type binds now and logs its rules under its name'() {
    given:
    log.level = Level.FINE

    when:
    vehicles(new CodeFormat(), new BatchLimit()).collectionBinding(CreateVehicle).rules(BatchLimit, CodeFormat).name(name).build()

    then:
    logged*.message == ["Rules bound to $label: [vehicle.batch-limit, vehicle.code-format]".toString()]

    where:
    name || label
    CREATE || "a collection of ${CREATE}"
    'VehicleService.createAll' || "VehicleService.createAll (a collection of ${CREATE})"
  }

  def 'a binding without a name is named after its element type, in logs and in its description'() {
    given:
    log.level = Level.FINE
    def binding = vehicles(new CodeFormat(), new BatchLimit()).collectionBinding(CreateVehicle).rules(BatchLimit, CodeFormat).exclude().build()

    expect:
    logged*.message == ["Rules bound to a collection of ${CREATE}: [vehicle.batch-limit, vehicle.code-format]".toString()]
    binding.toString() == "CollectionBinding[name=${CREATE}, elements=${CREATE}, mode=COLLECT_ALL, rules=[vehicle.batch-limit, vehicle.code-format], excluded=[]]"
  }

  def 'a binding describes its excluded rules and its own mode'() {
    given:
    def binding = vehicles(new NameUnique(), new BatchLimit()).collectionBinding(VehicleFields).exclude(NameUnique, BatchLimit).mode(CheckMode.FAIL_FAST).name('all').build()

    expect:
    binding.mode() == CheckMode.FAIL_FAST
    binding.toString() == "CollectionBinding[name=all, elements=${FIELDS}, mode=FAIL_FAST, rules=[], excluded=[vehicle.batch-limit, vehicle.name-unique]]"
  }

  def 'a binding takes the mode of the Muster unless it has its own'() {
    expect:
    vehicles().collectionBinding(CreateVehicle).build().mode() == CheckMode.COLLECT_ALL
  }

  def 'a binding passes an empty collection, and one whose elements pass, for #type.simpleName'() {
    expect:
    vehicles(new CodeFormat(), new BatchLimit()).collectionBinding(type).build().check(inputs)

    where:
    [type, inputs] << [[VehicleFields, CreateVehicle], [[], [VALID]]].combinations()
  }

  def 'a binding of a type that is not final rejects elements of different classes'() {
    when:
    vehicles().collectionBinding(VehicleFields).build().check([VALID, new UpdateVehicle(1L, 'TR-2', 'Zeus')])

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "the elements of a checked collection must be of one class; the element at [0] is a ${CREATE}, the element at [1] a ${UpdateVehicle.typeName}"
  }

  def 'a binding rejects an element of another type, which only unchecked code can pass'() {
    when:
    vehicles().collectionBinding(CreateVehicle).build().check([VALID, 'two'])

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "the binding checks ${CREATE}; the element at [1] is a java.lang.String"
  }

  def 'a binding name must not be blank: "#name"'() {
    when:
    vehicles().collectionBinding(CreateVehicle).name(name)

    then:
    def e = thrown(exception)
    e.message == message

    where:
    name || exception                | message
    ' ' || IllegalArgumentException | 'name must not be blank'
    null || NullPointerException     | 'name'
  }

  def 'a binding needs an element type'() {
    when:
    vehicles().collectionBinding(null)

    then:
    def e = thrown(NullPointerException)
    e.message == 'elementType'
  }

  def 'Muster checks a collection of rule targets with the rules of the whole collection bound to their class'() {
    given:
    def muster = vehicles(new CodeFormat(), new BatchLimit(), new NamesDistinct())

    when:
    muster.checkAll(THREE)

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == ['vehicle.batch-limit -', 'vehicle.code-format [1]']
  }

  def 'Muster checks a single rule target without the rules of the whole collection, also after a collection'() {
    given:
    def limit = new BatchLimit()
    def muster = vehicles(limit)

    when:
    muster.checkAll([VALID])
    muster.check(VALID)

    then:
    limit.received == [[VALID]]
  }

  def 'threads that check a collection of a new class at the same time bind its rules once, and each runs them: #how'() {
    given:
    log.level = Level.FINE
    def counted = new CountedVehicle()
    def limit = new BatchLimit()
    def muster = vehicles(counted, limit)
    def binding = muster.collectionBinding(VehicleFields).build()
    logged.clear()

    when:
    concurrently { check(muster, binding) }

    then:
    counted.checked.size() == 32
    limit.received.size() == 16
    logged*.message.findAll {
      it.startsWith('Rules bound')
    } == ["Rules bound to $label: [vehicle.batch-limit, vehicle.counted]".toString()]
    logged.findAll { it.level == Level.WARNING }*.message == [
      "Rule 'vehicle.counted' checks the elements of a collection one by one, since it does not override checkAll; override it to check them at once, or call BusinessRule.super.checkAll from it to keep checking them one by one"
    ]

    where:
    how             | check || label
    'a binding'     | { Muster m, CollectionBinding b ->
      b.check([VALID, VALID])
    } || "${FIELDS} (a collection of ${CREATE})"
    'Muster itself' | { Muster m, CollectionBinding b ->
      m.checkAll([VALID, VALID])
    } || "a collection of ${CREATE}"
  }

  def 'elements of a type that is not a rule target may be of different classes, checked by one chain bound when the binding is built'() {
    given:
    log.level = Level.FINE
    def binding = vehicles(new AmountPositive(), new AmountLimit()).collectionBinding(Number).rules(AmountLimit, AmountPositive).build()

    expect:
    logged*.message == ['Rules bound to a collection of java.lang.Number: [amount.limit, amount.positive]']

    when:
    binding.check([1, 2L])

    then:
    noExceptionThrown()

    when:
    binding.check([1, 2L, -3.5d])

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == ['amount.limit -', 'amount.positive [2]']
  }

  def 'a binding of a type that is not a rule target checks elements of different classes with checkAll'() {
    given:
    def binding = vehicles(new AmountPositive()).binding(Number).rules(AmountPositive).build()

    when:
    binding.checkAll([1, -2L, 3.5d])

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == ['amount.positive [1]']
  }

  def 'a binding tells the chains of the element classes checked so far, or its one chain for a type bound when it was built'() {
    given:
    def targets = vehicles(new BatchLimit(), new CodeFormat()).collectionBinding(VehicleFields).build()
    def longs = vehicles(new TaskLimit()).collectionBinding(Long).rules(TaskLimit).build()

    expect:
    targets.chains().isEmpty()
    longs.chains().keySet().toList() == [Long]
    longs.chains()[Long].ruleIds() == ['task.limit']

    when:
    targets.check([VALID])

    then:
    targets.chains().keySet().toList() == [CreateVehicle]
    targets.chains()[CreateVehicle].ruleIds() == ['vehicle.batch-limit', 'vehicle.code-format']
  }
}
