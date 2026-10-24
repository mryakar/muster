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

/** Rules that require other rules: order, skipping, and the rules a chain adds. */
class RuleDependencySpec extends EngineSpecification {

  def 'a rule runs after the rules it requires, whatever their ids'() {
    when:
    tasks().chain(Long, TaskAssignable, TaskExists).check(1L)

    then:
    ran == [
      'task.exists',
      'task.assignable'
    ]
  }

  def 'rules that do not depend on each other run in the order the chain lists them'() {
    given:
    def muster = Muster.builder().rules(new TaskExists(ran), new TaskLocked(ran)).build()

    when:
    muster.chain(Long, TaskLocked, TaskExists).check(1L)

    then:
    ran == ['task.locked', 'task.exists']
  }

  def 'a rule the chain adds runs just before the first listed rule that requires it'() {
    given:
    def muster = Muster.builder().rules(new TaskExists(ran), new TaskLocked(ran), new TaskReopened(ran)).build()

    when:
    muster.chain(Long, TaskReopened, TaskLocked).check(1L)

    then:
    ran == [
      'task.exists',
      'task.reopened',
      'task.locked'
    ]
  }

  def 'listed rules that a listed rule requires run before it in the order they are listed'() {
    given:
    def muster = Muster.builder().rules(new TaskExists(ran), new TaskLocked(ran), new TaskClosable(ran)).build()

    when:
    muster.chain(Long, TaskClosable, TaskLocked, TaskExists).check(1L)

    then:
    ran == [
      'task.locked',
      'task.exists',
      'task.closable'
    ]
  }

  def 'of the rules a listed rule requires, the listed ones run first'() {
    given:
    def muster = Muster.builder().rules(new TaskExists(ran), new TaskLocked(ran), new TaskClosable(ran)).build()

    when:
    muster.chain(Long, TaskClosable, TaskLocked).check(1L)

    then:
    ran == [
      'task.locked',
      'task.exists',
      'task.closable'
    ]
  }

  def 'when task #task fails #failing, the rules that require it are skipped'() {
    when:
    tasks().chain(Long, TaskStarted, TaskAssignable, TaskExists).check(task)

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.ruleId() == [failing]
    ran == expectedRan

    where:
    task | failing || expectedRan
    404L | 'task.exists' || ['task.exists']
    409L | 'task.assignable' || [
      'task.exists',
      'task.assignable'
    ]
    410L | 'task.started' || [
      'task.exists',
      'task.assignable',
      'task.started'
    ]
  }

  def 'a rule is skipped only when one of its own required rules did not pass'() {
    given: 'the order is exists, locked, closable, reopened'
    def chain = Muster.builder()
        .rules(new TaskExists(ran), new TaskLocked(ran), new TaskClosable(ran), new TaskReopened(ran))
        .mode(CheckMode.COLLECT_ALL)
        .build()
        .chain(Long, TaskClosable, TaskReopened)

    when:
    chain.check(423L)

    then: 'closable needs locked, which failed; reopened needs only exists'
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.ruleId() == ['task.locked']
    ran == [
      'task.exists',
      'task.locked',
      'task.reopened'
    ]
  }

  def 'a required rule that several listed rules share is added and logged once, and gates them all'() {
    given:
    def chain = Muster.builder().rules(new TaskExists(ran), new TaskAssignable(ran), new TaskReopened(ran))
        .mode(CheckMode.COLLECT_ALL).build()
        .chain(Long, TaskAssignable, TaskReopened)

    expect:
    logged*.message == [
      "The chain for java.lang.Long includes rule 'task.exists' because 'task.assignable' requires it"
    ]

    when:
    chain.check(404L)

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.ruleId() == ['task.exists']
    ran == ['task.exists']
  }

  def 'a rule whose required rule does not apply is skipped'() {
    when:
    tasks().chain(Long, TaskFromImport, TaskImported).check(1L)

    then:
    noExceptionThrown()
    ran.empty
  }

  def 'a chain includes the rules that its rules require, and logs each one'() {
    when:
    tasks().chain(Long, TaskStarted).check(1L)

    then:
    ran == [
      'task.exists',
      'task.assignable',
      'task.started'
    ]
    logged*.level == [Level.INFO, Level.INFO]
    logged*.message == [
      "The chain for java.lang.Long includes rule 'task.assignable' because 'task.started' requires it",
      "The chain for java.lang.Long includes rule 'task.exists' because 'task.assignable' requires it"
    ]
  }

  def 'a chain that lists the required rules logs nothing'() {
    when:
    tasks().chain(Long, TaskStarted, TaskAssignable, TaskExists)

    then:
    logged.empty
  }

  def 'a rule may be disabled while the rules it requires stay enabled'() {
    when:
    Muster.builder().rules(new TaskExists(ran), new TaskAssignable(ran), new TaskStarted(ran)).disable('task.started').build()
        .chain(Long, TaskStarted, TaskAssignable, TaskExists).check(410L)

    then:
    noExceptionThrown()
    ran == [
      'task.exists',
      'task.assignable'
    ]
  }

  def 'a disabled rule adds none of the rules it requires to a chain'() {
    when:
    Muster.builder().rules(new TaskExists(ran), new TaskAssignable(ran)).disable('task.exists', 'task.assignable').build()
        .chain(Long, TaskAssignable).check(404L)

    then:
    noExceptionThrown()
    ran.empty
    logged*.level == [Level.WARNING, Level.WARNING]
  }
}
