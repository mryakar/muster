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

import java.security.Principal
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Level
import me.yakar.muster.EngineSpecification.TaskExists
import me.yakar.muster.EngineSpecification.VehicleFields
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.RequiresRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult

/** Checking the elements of a collection in one call: each rule, its requirements per element, and the modes. */
class CollectionCheckSpec extends EngineSpecification {

  /** Assigns the tasks that exist at once; 409 is not assignable. */
  @Rule(id = 'task.assignable-at-once')
  @RequiresRule(TaskExists)
  static class AssignableAtOnce implements BusinessRule<Long> {
    final List<List<Long>> received = new CopyOnWriteArrayList<>()

    RuleResult check(Long input, RuleContext ctx) {
      throw new AssertionError('checked one by one')
    }

    RuleResult checkAll(List<? extends Long> inputs, RuleContext ctx) {
      received << List.copyOf(inputs)
      def violations = []
      inputs.eachWithIndex { input, i ->
        if (input == 409L) {
          violations << ctx.element(i).violation().build()
        }
      }
      violations ? RuleResult.failed(violations) : RuleResult.passed()
    }
  }

  /** Notes the tasks it checks; requires the rule that checks them at once. */
  @Rule(id = 'task.notified')
  @RequiresRule(AssignableAtOnce)
  static class Notified implements BusinessRule<Long> {
    final List<Long> checked = new CopyOnWriteArrayList<>()

    RuleResult check(Long input, RuleContext ctx) {
      checked << input
      RuleResult.passed()
    }

    RuleResult checkAll(List<? extends Long> inputs, RuleContext ctx) {
      BusinessRule.super.checkAll(inputs, ctx)
    }
  }

  /** Fails at once, with the targets it is given; a null target is a violation without one. */
  @Rule(id = 'task.assignable-at-once')
  @RequiresRule(TaskExists)
  static class FailsWith implements BusinessRule<Long> {
    final List<String> targets

    FailsWith(List<String> targets) {
      this.targets = targets
    }

    RuleResult check(Long input, RuleContext ctx) {
      throw new AssertionError('checked one by one')
    }

    RuleResult checkAll(List<? extends Long> inputs, RuleContext ctx) {
      RuleResult.failed(targets.collect {
        it == null ? ctx.violation().build() : ctx.violation().target(it).build()
      })
    }
  }

  /** Notes the tasks it checks; requires the rule that fails with the targets it is given. */
  @Rule(id = 'task.notified')
  @RequiresRule(FailsWith)
  static class NotifiedAfterFailing extends Notified {}

  /** Counts how often it is asked whether it applies and how often it checks; requires a task to exist. */
  @Rule(id = 'task.counted')
  @RequiresRule(TaskExists)
  static class Counted implements BusinessRule<Long> {
    int asked
    int checkedAll

    boolean appliesTo(RuleContext ctx) {
      asked++
      true
    }

    RuleResult check(Long input, RuleContext ctx) {
      RuleResult.passed()
    }

    RuleResult checkAll(List<? extends Long> inputs, RuleContext ctx) {
      checkedAll++
      RuleResult.passed()
    }
  }

  /** Counts how often it is asked whether it applies; checked one by one. */
  @Rule(id = 'task.counted-one-by-one')
  static class CountedOneByOne implements BusinessRule<Long> {
    int asked

    boolean appliesTo(RuleContext ctx) {
      asked++
      true
    }

    RuleResult check(Long input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  /** Clears the list of the caller while it checks the first element, and notes what it checks. */
  @Rule(id = 'task.clears-list')
  static class ClearsCallersList implements BusinessRule<Long> {
    final List<Long> callers
    final List<Long> checked = []

    ClearsCallersList(List<Long> callers) {
      this.callers = callers
    }

    RuleResult check(Long input, RuleContext ctx) {
      callers.clear()
      checked << input
      RuleResult.passed()
    }
  }

  /** Returns null for the input 'b' only. */
  @Rule(id = 'rule.returns-null')
  static class ReturnsNullForB implements BusinessRule<String> {
    RuleResult check(String input, RuleContext ctx) {
      input == 'b' ? null : RuleResult.passed()
    }
  }

  /** Asks for the context of an element past the inputs it received. */
  @Rule(id = 'rule.reaches-too-far')
  static class ReachesTooFar implements BusinessRule<Long> {
    RuleResult check(Long input, RuleContext ctx) {
      RuleResult.passed()
    }

    RuleResult checkAll(List<? extends Long> inputs, RuleContext ctx) {
      RuleResult.failed(ctx.element(inputs.size()).violation().build())
    }
  }

  @Rule(id = 'rule.returns-null-at-once')
  static class ReturnsNullAtOnce implements BusinessRule<Long> {
    RuleResult check(Long input, RuleContext ctx) {
      RuleResult.passed()
    }

    RuleResult checkAll(List<? extends Long> inputs, RuleContext ctx) {
      null
    }
  }

  @Rule(id = 'rule.impostor-at-once')
  static class ImpostorAtOnce implements BusinessRule<Long> {
    RuleResult check(Long input, RuleContext ctx) {
      RuleResult.passed()
    }

    RuleResult checkAll(List<? extends Long> inputs, RuleContext ctx) {
      RuleResult.failed(RuleContext.forRule('task.exists').violation().build())
    }
  }

  @Rule(id = 'call.reported-at-once')
  static class ReportsCallAtOnce implements BusinessRule<Object> {
    RuleResult check(Object input, RuleContext ctx) {
      RuleResult.passed()
    }

    RuleResult checkAll(List<?> inputs, RuleContext ctx) {
      RuleResult.failed(ctx.violation()
          .clientArg('caller', ctx.caller().map { it.name }.orElse('none'))
          .clientArg('locale', ctx.locale().toLanguageTag())
          .build())
    }
  }

  Muster.Builder tasks(BusinessRule<?>... rules) {
    Muster.builder().rules(rules).mode(CheckMode.COLLECT_ALL)
  }

  def 'an empty collection passes without running a rule or logging'() {
    given:
    def chain = tasks(new TaskExists(ran)).build().chain(Long, TaskExists)
    logged.clear()

    when:
    chain.checkAll([])

    then:
    ran.empty
    logged.empty
  }

  def 'a rule that does not override checkAll checks each element, and violations name its position'() {
    given:
    def chain = tasks(new TaskExists(ran)).build().chain(Long, TaskExists)

    when:
    chain.checkAll([1L, 404L, 2L])

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == ['task.exists [1]']
    ran == ['task.exists'] * 3
  }

  def 'a rule checked one by one is logged once, as a warning, whatever the number of checks'() {
    given:
    def chain = tasks(new TaskExists(ran)).build().chain(Long, TaskExists)
    logged.clear()

    when:
    2.times { chain.checkAll([1L, 2L]) }

    then:
    logged*.level == [Level.WARNING]
    logged*.message == ["Rule 'task.exists' " + ONE_BY_ONE]
  }

  def 'in fail-fast mode, a rule checked one by one stops at the first violated element'() {
    given:
    def chain = tasks(new TaskExists(ran), new TaskLocked(ran)).build().chain(Long, TaskExists, TaskLocked).withMode(CheckMode.FAIL_FAST)

    when:
    chain.checkAll([404L, 404L, 1L])

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == ['task.exists [0]']
    ran == ['task.exists']
  }

  def 'in collect-all mode, every element of every rule is reported, in the order of the rules'() {
    given:
    def chain = tasks(new TaskExists(ran), new TaskLocked(ran)).build().chain(Long, TaskExists, TaskLocked)

    when:
    chain.checkAll([423L, 404L, 404L])

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == ['task.exists [1]', 'task.exists [2]', 'task.locked [0]']
  }

  def 'a rule checks the elements that passed the rules it requires; the others are skipped for it'() {
    given:
    def chain = tasks(new TaskExists(ran), new TaskAssignable(ran)).build().chain(Long, TaskExists, TaskAssignable)

    when:
    chain.checkAll([404L, 409L, 1L])

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == ['task.exists [0]', 'task.assignable [1]']
    ran == ['task.exists'] * 3 + ['task.assignable'] * 2
  }

  def 'a rule that overrides checkAll gets the whole collection once, and can find conflicts within it'() {
    given:
    def rule = new NamesFree()
    def chain = tasks(rule).build().chain(CreateVehicle, NamesFree)
    def vehicles = [
      new CreateVehicle('TR-1', 'Atlas'),
      new CreateVehicle('TR-2', 'Atlas'),
      new CreateVehicle('TR-3', 'taken')
    ]
    logged.clear()

    when:
    chain.checkAll(vehicles)

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == ['vehicle.names-free [1].name', 'vehicle.names-free [2].name']
    rule.received == [vehicles]
    logged.empty
  }

  def 'the list that checkAll receives cannot be changed'() {
    given:
    def rule = new NamesFree()
    tasks(rule).build().chain(CreateVehicle, NamesFree).checkAll([VALID])

    when:
    rule.received[0].clear()

    then:
    thrown(UnsupportedOperationException)
  }

  def 'a rule sees a copy of the collection, taken when the check starts'() {
    given:
    def callers = new ArrayList<Long>([1L, 2L, 3L])
    def rule = new ClearsCallersList(callers)

    when:
    tasks(rule).build().chain(Long, ClearsCallersList).checkAll(callers)

    then:
    callers.empty
    rule.checked == [1L, 2L, 3L]
  }

  def 'a rule checking at once receives a copy, not the list of the caller'() {
    given:
    def rule = new NamesFree()
    def vehicles = [VALID]

    when:
    tasks(rule).build().chain(CreateVehicle, NamesFree).checkAll(vehicles)

    then:
    rule.received == [vehicles]
    !rule.received[0].is(vehicles)
  }

  def 'a collection that is not a list reaches checkAll as a list, in its order'() {
    given:
    def rule = new NamesFree()
    def vehicles = new LinkedHashSet([new CreateVehicle('TR-1', 'Atlas'), new CreateVehicle('TR-2', 'Zeus')])

    when:
    tasks(rule).build().chain(CreateVehicle, NamesFree).checkAll(vehicles)

    then:
    rule.received == [vehicles as List]
  }

  def 'a rule that overrides checkAll gets the elements that passed what it requires, and violations name their position in the collection'() {
    given:
    def atOnce = new AssignableAtOnce()
    def chain = tasks(new TaskExists(ran), atOnce).build().chain(Long, TaskExists, AssignableAtOnce)

    when:
    chain.checkAll([404L, 409L, 1L])

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == ['task.exists [0]', 'task.assignable-at-once [1]']
    atOnce.received == [[409L, 1L]]
  }

  def 'the elements that a rule checking at once does not name passed it'() {
    given:
    def notified = new Notified()
    def chain = tasks(new TaskExists(ran), new AssignableAtOnce(), notified).build().chain(Long, TaskExists, AssignableAtOnce, Notified)

    when:
    chain.checkAll([1L, 409L, 404L, 2L])

    then:
    thrown(BusinessRuleViolationException)
    notified.checked == [1L, 2L]
  }

  def 'a violation of a rule checking at once that names no checked element is about all of them: #targets'() {
    given:
    def notified = new NotifiedAfterFailing()
    def chain = tasks(new TaskExists(ran), new FailsWith(targets), notified).build().chain(Long, TaskExists, FailsWith, NotifiedAfterFailing)

    when:
    chain.checkAll([1L, 404L, 2L])

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.ruleId() == ['task.exists'] + ['task.assignable-at-once'] * targets.size()
    notified.checked.empty

    where:
    targets << [[null], ['name'], ['[0]', '[1]'], ['[7]'], ['[]'], ['[x].name'], ['[-1]'], ['[0]', null]]
  }

  def 'a violation of a rule checking at once that names checked elements fails only them'() {
    given:
    def notified = new NotifiedAfterFailing()
    def chain = tasks(new TaskExists(ran), new FailsWith(['[2].code', '[0]']), notified).build().chain(Long, TaskExists, FailsWith, NotifiedAfterFailing)

    when:
    chain.checkAll([1L, 404L, 2L, 3L])

    then:
    thrown(BusinessRuleViolationException)
    notified.checked == [3L]
  }

  def 'in fail-fast mode, a rule checking at once reports its violations and no later rule runs'() {
    given:
    def chain = tasks(new TaskExists(ran), new FailsWith(['[1]', '[2]']), new TaskLocked(ran)).build().chain(Long, TaskExists, FailsWith, TaskLocked).withMode(CheckMode.FAIL_FAST)

    when:
    chain.checkAll([1L, 2L, 3L])

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == ['task.assignable-at-once [1]', 'task.assignable-at-once [2]']
    ran == ['task.exists'] * 3
  }

  def 'the rules that require a rule that does not apply check no element of a collection'() {
    given:
    def chain = tasks(new TaskImported(ran), new TaskFromImport(ran)).build().chain(Long, TaskFromImport)

    when:
    chain.checkAll([1L, 2L])

    then:
    ran.empty
  }

  def 'when a rule checking at once passes every element, the rules that require it check them all'() {
    given:
    def notified = new Notified()
    def chain = tasks(new TaskExists(ran), new AssignableAtOnce(), notified).build().chain(Long, TaskExists, AssignableAtOnce, Notified)

    when:
    chain.checkAll([1L, 2L, 3L])

    then:
    notified.checked == [1L, 2L, 3L]
  }

  def 'a rule is asked once whether it applies to a collection: #how'() {
    when:
    tasks(rule, new TaskExists(ran)).build().chain(Long, rule.getClass()).checkAll([1L, 2L, 3L])

    then:
    rule.asked == 1

    where:
    how            | rule
    'at once'      | new Counted()
    'one by one'   | new CountedOneByOne()
  }

  def 'a rule with no element left to check is not asked whether it applies, and checks nothing'() {
    given:
    def counted = new Counted()

    when:
    tasks(new TaskExists(ran), counted).build().chain(Long, TaskExists, Counted).checkAll([404L, 404L])

    then:
    thrown(BusinessRuleViolationException)
    counted.asked == 0
    counted.checkedAll == 0
  }

  def 'a rule that checks nothing of a collection is not logged as checked one by one'() {
    given:
    def chain = tasks(new TaskExists(ran), new TaskAssignable(ran), new TaskImported(ran), new TaskFromImport(ran)).build()
        .chain(Long, TaskAssignable, TaskFromImport)
    logged.clear()

    when:
    chain.checkAll([404L])

    then:
    thrown(BusinessRuleViolationException)
    logged*.message.findAll { it.endsWith(ONE_BY_ONE) } == ["Rule 'task.exists' " + ONE_BY_ONE]
  }

  def 'a rule checking at once learns the caller and the locale of the call'() {
    given:
    def chain = Muster.builder().rules(new ReportsCallAtOnce())
        .caller { ({ 'amo' } as Principal) }
        .locale { Locale.forLanguageTag('tr-TR') }
        .build().chain(Long, ReportsCallAtOnce)

    when:
    chain.checkAll([1L])

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()[0].clientArgs() == [caller: 'amo', locale: 'tr-TR']
  }

  def 'a rule checking at once that #problem fails the check'() {
    given:
    def chain = tasks(rule).build().chain(Long, rule.getClass())

    when:
    chain.checkAll([1L])

    then:
    def e = thrown(exception)
    e.message == message

    where:
    problem                               | rule || exception             | message
    'returns null'                        | new ReturnsNullAtOnce() || NullPointerException  | "rule 'rule.returns-null-at-once' returned null"
    'reports a violation of another rule' | new ImpostorAtOnce() || IllegalStateException | "rule 'rule.impostor-at-once' reported a violation of rule 'task.exists'; build violations from the RuleContext the rule receives"
  }

  def 'a rule checking at once cannot name an element past the inputs it received'() {
    when:
    tasks(new ReachesTooFar()).build().chain(Long, ReachesTooFar).checkAll([1L, 2L])

    then:
    def e = thrown(IllegalArgumentException)
    e.message == 'the index 2 is not that of one of the 2 inputs'
  }

  def 'a rule checked one by one that returns null fails the check'() {
    given:
    def chain = tasks(new ReturnsNullForB()).build().chain(String, ReturnsNullForB)

    when:
    chain.checkAll(['a', 'b'])

    then:
    def e = thrown(NullPointerException)
    e.message == "rule 'rule.returns-null' returned null for the element at [1]"
  }

  def 'a collection is checked element by element: #problem'() {
    given:
    def chain = tasks(new TaskExists(ran)).build().chain(Long, TaskExists)

    when:
    chain.checkAll(inputs)

    then:
    def e = thrown(exception)
    e.message == message
    ran.empty

    where:
    problem                      | inputs || exception                | message
    'no collection'              | null || NullPointerException     | 'inputs'
    'a null element'             | [1L, null] || NullPointerException     | 'the element at [1] is null'
    'an element of another type' | [1L, 'two'] || IllegalArgumentException | 'the chain checks java.lang.Long; the element at [1] is a java.lang.String'
  }

  def 'elements of different classes are fine for a chain, which checks its type'() {
    given:
    def chain = tasks(new NameUnique()).build().chain(VehicleFields, NameUnique)

    when:
    chain.checkAll([VALID, new UpdateVehicle(1L, 'TR-2', 'taken')])

    then:
    def e = thrown(BusinessRuleViolationException)
    described(e) == ['vehicle.name-unique [1]']
  }

  def 'a rule checked one by one from many threads at once is logged once'() {
    given:
    def chain = tasks(new TaskExists(ran)).build().chain(Long, TaskExists)
    logged.clear()

    when:
    concurrently {
      1000.times { chain.checkAll([1L, 2L]) }
    }

    then:
    logged.findAll { it.level == Level.WARNING }*.message == ["Rule 'task.exists' " + ONE_BY_ONE]
    ran.size() == 32000
  }
}
