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

import java.security.Principal
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.RequiresRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import me.yakar.muster.rule.RuleTarget
import me.yakar.muster.spi.RuleFactory
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

  static record Unbound(String text) implements RuleTarget {}

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

  @Rule(id = 'import.only')
  static class ImportOnly implements BusinessRule<Object> {
    boolean appliesTo(RuleContext ctx) {
      false
    }

    RuleResult check(Object input, RuleContext ctx) {
      RuleResult.failed(ctx.violation().build())
    }
  }

  @Rule(id = 'rule.returns-null')
  static class ReturnsNull implements BusinessRule<Object> {
    RuleResult check(Object input, RuleContext ctx) {
      null
    }
  }

  @Rule(id = 'rule.broken')
  static class Broken implements BusinessRule<Object> {
    static final RuntimeException FAILURE = new IllegalStateException('database down')

    RuleResult check(Object input, RuleContext ctx) {
      throw FAILURE
    }
  }

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

  /**
   * A rule about a task that notes when it runs, and fails for one task number, which reads like
   * an HTTP status:
   *
   * <ul>
   *   <li>404 — {@code task.exists}
   *   <li>409 — {@code task.assignable}, which requires {@code task.exists}
   *   <li>410 — {@code task.started}, which requires {@code task.assignable}
   *   <li>423 — {@code task.locked}
   *   <li>500 — {@code task.audited} and {@code task.billed}, both at once
   *   <li>1 — {@code task.from-import}, which requires {@code task.imported}; that rule never applies
   *   <li>-1 — never: {@code task.closable}, {@code task.reopened}, {@code task.imported}
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

  @Rule(id = 'task.started')
  @RequiresRule(TaskAssignable)
  static class TaskStarted extends TaskRule {
    TaskStarted(List<String> ran) {
      super(ran, 410L)
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

  @Rule(id = 'task.closable')
  @RequiresRule([TaskExists, TaskLocked])
  static class TaskClosable extends TaskRule {
    TaskClosable(List<String> ran) {
      super(ran, -1L)
    }
  }

  @Rule(id = 'task.reopened')
  @RequiresRule(TaskExists)
  static class TaskReopened extends TaskRule {
    TaskReopened(List<String> ran) {
      super(ran, -1L)
    }
  }

  @Rule(id = 'vehicle.known')
  static class VehicleKnown implements BusinessRule<Identified> {
    RuleResult check(Identified input, RuleContext ctx) {
      input.id() == 404L ? RuleResult.failed(ctx.violation().build()) : RuleResult.passed()
    }
  }

  @Rule(id = 'vehicle.a-renamable')
  @RequiresRule(VehicleKnown)
  static class VehicleRenamable implements BusinessRule<UpdateVehicle> {
    RuleResult check(UpdateVehicle input, RuleContext ctx) {
      RuleResult.failed(ctx.violation().build())
    }
  }

  @Rule(id = 'task.audited')
  static class TaskAudited extends TaskRule {
    TaskAudited(List<String> ran) {
      super(ran, 500L)
    }
  }

  @Rule(id = 'task.billed')
  static class TaskBilled extends TaskRule {
    TaskBilled(List<String> ran) {
      super(ran, 500L)
    }
  }

  @Rule(id = 'plan.capacity-sufficient')
  static class CapacitySufficient implements BusinessRule<Long> {
    RuleResult check(Long input, RuleContext ctx) {
      RuleResult.failed(ctx.violation('plan.passenger-capacity').build(), ctx.violation('plan.parcel-capacity').build())
    }
  }

  @Rule(id = 'vehicle.impostor')
  static class Impostor implements BusinessRule<Object> {
    RuleResult check(Object input, RuleContext ctx) {
      RuleResult.failed(ctx.violation().build(), RuleContext.forRule('vehicle.name-unique').violation().build())
    }
  }

  def amo = { 'amo' } as Principal

  List<String> ran = []
  List<LogRecord> logged = []
  Logger log = Logger.getLogger('me.yakar.muster')
  Handler handler = new Handler() {
    void publish(LogRecord record) {
      logged << record
    }

    void flush() {}

    void close() {}
  }

  def setup() {
    log.addHandler(handler)
  }

  def cleanup() {
    log.removeHandler(handler)
  }

  /** In FAIL_FAST mode a failed rule ends the check, so only COLLECT_ALL shows which rules are skipped. */
  Muster tasks() {
    Muster.builder()
        .rules(new TaskExists(ran), new TaskAssignable(ran), new TaskStarted(ran), new TaskImported(ran), new TaskFromImport(ran))
        .mode(CheckMode.COLLECT_ALL)
        .build()
  }
}
