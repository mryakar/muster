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

import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.LogRecord
import java.util.logging.Logger
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.RequiresRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import me.yakar.muster.rule.RuleTarget
import me.yakar.muster.spi.CheckListener
import me.yakar.muster.spi.CheckListener.Check
import me.yakar.muster.spi.CheckListener.CheckEnd
import me.yakar.muster.spi.CheckListener.Observer
import me.yakar.muster.spi.CheckListener.RuleEnd
import spock.lang.Specification

/** The rules, inputs and log capture that the engine specifications share. */
abstract class EngineSpecification extends Specification {

  static interface VehicleFields extends RuleTarget {
    String code()

    String name()
  }

  static interface Identified {
    Long id()
  }

  static record CreateVehicle(String code, String name) implements VehicleFields {}

  static record UpdateVehicle(Long id, String code, String name) implements VehicleFields, Identified {}

  /** What Muster warns of a rule checked one by one, after its id. */
  static final String ONE_BY_ONE = "checks the elements of a collection one by one, since it does not override checkAll; override it to check them at once, or call BusinessRule.super.checkAll from it to keep checking them one by one"

  /** The violations of a rejected check, each as its rule id and its target, or '-' for none. */
  static List<String> described(BusinessRuleViolationException e) {
    e.violations().collect { it.ruleId() + ' ' + it.target().orElse('-') }
  }

  static final CreateVehicle VALID = new CreateVehicle('TR-1', 'Atlas')
  static final CreateVehicle INVALID = new CreateVehicle('bad', 'taken')

  @Rule(id = 'vehicle.name-unique')
  static class NameUnique implements BusinessRule<VehicleFields> {
    RuleResult check(VehicleFields input, RuleContext ctx) {
      input.name() == 'taken' ? RuleResult.failed(ctx.violation().clientArg('name', input.name()).build()) : RuleResult.passed()
    }
  }

  @Rule(id = 'vehicle.code-format')
  static class CodeFormat implements BusinessRule<VehicleFields> {
    RuleResult check(VehicleFields input, RuleContext ctx) {
      input.code() ==~ /[A-Z]{2}-\d+/ ? RuleResult.passed() : RuleResult.failed(ctx.violation().build())
    }
  }

  @Rule(id = 'vehicle.exists')
  static class VehicleExists implements BusinessRule<Long> {
    RuleResult check(Long input, RuleContext ctx) {
      input == 404L ? RuleResult.failed(ctx.violation().build()) : RuleResult.passed()
    }
  }

  @Rule(id = 'call.reported')
  static class ReportsCall implements BusinessRule<Object> {
    RuleResult check(Object input, RuleContext ctx) {
      RuleResult.failed(ctx.violation()
          .clientArg('caller', ctx.caller().map { it.name }.orElse('none'))
          .clientArg('locale', ctx.locale().toLanguageTag())
          .build())
    }
  }

  /**
   * A rule about a task that notes when it runs, and fails for one task number, which reads like
   * an HTTP status. The specs add rules of their own.
   *
   * <ul>
   *   <li>404 — {@code task.exists}
   *   <li>409 — {@code task.assignable}, which requires {@code task.exists}
   *   <li>423 — {@code task.locked}
   *   <li>1 — {@code task.from-import}, which requires {@code task.imported}; that rule never applies
   *   <li>-1 — never: {@code task.imported}
   * </ul>
   */
  static abstract class TaskRule implements BusinessRule<Long> {
    final List<String> ran
    final long failsFor

    TaskRule(List<String> ran, long failsFor) {
      this.ran = ran
      this.failsFor = failsFor
    }

    RuleResult check(Long input, RuleContext ctx) {
      ran << ctx.ruleId()
      input == failsFor ? RuleResult.failed(ctx.violation().build()) : RuleResult.passed()
    }
  }

  @Rule(id = 'task.exists')
  static class TaskExists extends TaskRule {
    TaskExists(List<String> ran) {
      super(ran, 404L)
    }
  }

  @Rule(id = 'task.assignable')
  @RequiresRule(TaskExists)
  static class TaskAssignable extends TaskRule {
    TaskAssignable(List<String> ran) {
      super(ran, 409L)
    }
  }

  @Rule(id = 'task.imported')
  static class TaskImported extends TaskRule {
    TaskImported(List<String> ran) {
      super(ran, -1L)
    }

    boolean appliesTo(RuleContext ctx) {
      false
    }
  }

  @Rule(id = 'task.from-import')
  @RequiresRule(TaskImported)
  static class TaskFromImport extends TaskRule {
    TaskFromImport(List<String> ran) {
      super(ran, 1L)
    }
  }

  @Rule(id = 'task.locked')
  static class TaskLocked extends TaskRule {
    TaskLocked(List<String> ran) {
      super(ran, 423L)
    }
  }

  /** Records every event of every check it observes, as a list of the kind and the value, per check. */
  static class Recording implements CheckListener, Observer {
    final List<Check> checks = new CopyOnWriteArrayList<>()
    final List<List> events = new CopyOnWriteArrayList<>()

    Observer checkStarted(Check check) {
      checks << check
      events << []
      this
    }

    void ruleStarted(String ruleId) {
      events.last() << ['started', ruleId]
    }

    void ruleEnded(RuleEnd end) {
      events.last() << ['ended', end]
    }

    void checkEnded(CheckEnd end) {
      events.last() << ['check', end]
    }

    /** The events of the only check, as the kind, the rule id or outcome, and for an end its outcome. */
    List<List> summary() {
      assert events.size() == 1
      events.first().collect { kind, value ->
        switch (kind) {
          case 'started' -> [kind, value]
          case 'ended' -> [kind, value.ruleId(), value.outcome()]
          default -> [kind, value.outcome()]
        }
      }
    }

    /** The ends of the rules of the only check, by rule id. */
    Map<String, RuleEnd> ruleEnds() {
      events.first().findAll { it[0] == 'ended' }.collectEntries { [(it[1].ruleId()): it[1]] }
    }

    CheckEnd checkEnd() {
      events.first().last()[1]
    }
  }

  @Rule(id = 'rule.throwing')
  static class Throwing implements BusinessRule<Object> {
    final RuntimeException failure

    Throwing(RuntimeException failure) {
      this.failure = failure
    }

    RuleResult check(Object input, RuleContext ctx) {
      throw failure
    }
  }

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

  /** Looks the names of a collection up at once, and finds names taken in the collection itself. */
  @Rule(id = 'vehicle.names-free')
  static class NamesFree implements BusinessRule<VehicleFields> {
    final List<List<?>> received = new CopyOnWriteArrayList<>()

    RuleResult check(VehicleFields input, RuleContext ctx) {
      throw new AssertionError('checked one by one')
    }

    RuleResult checkAll(List<? extends VehicleFields> inputs, RuleContext ctx) {
      received << inputs
      def violations = []
      def seen = [] as Set
      inputs.eachWithIndex { input, i ->
        if (input.name() == 'taken' || !seen.add(input.name())) {
          violations << ctx.element(i).violation().target('name').build()
        }
      }
      violations ? RuleResult.failed(violations) : RuleResult.passed()
    }
  }

  @Rule(id = 'vehicle.impostor')
  static class Impostor implements BusinessRule<Object> {
    RuleResult check(Object input, RuleContext ctx) {
      RuleResult.failed(ctx.violation().build(), RuleContext.forRule('vehicle.name-unique').violation().build())
    }
  }

  @Rule(id = 'rule.returns-null')
  static class ReturnsNull implements BusinessRule<Object> {
    RuleResult check(Object input, RuleContext ctx) {
      null
    }
  }

  List<String> ran = new CopyOnWriteArrayList<String>()

  LogCapture capture
  // Threads run rules and log at the same time in the concurrency tests.
  List<LogRecord> logged
  Logger log

  def setup() {
    capture = new LogCapture()
    logged = capture.records
    log = capture.logger
  }

  def cleanup() {
    capture.close()
  }
}
