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
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.RequiresRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import me.yakar.muster.rule.RuleTarget
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

  List<String> ran = new CopyOnWriteArrayList<String>()
  // Threads run rules and log at the same time in the concurrency tests; a plain list would lose
  // entries.
  List<LogRecord> logged = new CopyOnWriteArrayList<LogRecord>()
  // The logger is shared by the whole JVM, so specs that change it must not run in parallel.
  Logger log = Logger.getLogger('me.yakar.muster')
  Level level
  Handler handler = new Handler() {
    void publish(LogRecord record) {
      logged << record
    }

    void flush() {}

    void close() {}
  }

  def setup() {
    level = log.level
    log.addHandler(handler)
  }

  def cleanup() {
    log.removeHandler(handler)
    log.level = level
  }
}
