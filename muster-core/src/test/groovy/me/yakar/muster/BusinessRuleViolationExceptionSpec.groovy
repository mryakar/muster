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

import java.io.InvalidObjectException

import me.yakar.muster.rule.Category
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.Violation
import spock.lang.Shared
import spock.lang.Specification

/** The exception a check throws: its message, its violations and its serialized form. */
class BusinessRuleViolationExceptionSpec extends Specification {

  static final List<Violation> WITHOUT_ARGUMENTS = [
    RuleContext.forRule('vehicle.name-unique').violation().build(),
    RuleContext.forRule('vehicle.owner-only').violation().category(Category.NOT_FOUND).build()
  ]

  @Shared
  Violation owner

  @Shared
  Violation ready

  def setupSpec() {
    owner = RuleContext.forRule('request.owner-only').violation('request.not-owner')
        .clientArg('requestNo', 'TR-1047')
        .internalArg('ownerId', 7731)
        .build()
    ready = RuleContext.forRule('task.ready').violation().clientArg('currentStatus', 'DRAFT').build()
  }

  def 'the message lists the codes and no argument values'() {
    expect:
    messageFor([owner]) == '1 business rule violation: request.not-owner'
    messageFor([owner, ready]) == '2 business rule violations: request.not-owner, task.ready'
  }

  def 'the message reaches the localized message and the description'() {
    when:
    def exception = new BusinessRuleViolationException([owner])

    then:
    exception.localizedMessage == '1 business rule violation: request.not-owner'
    exception.toString() == BusinessRuleViolationException.name + ': 1 business rule violation: request.not-owner'
  }

  def 'the exception keeps the violations in order and does not change with the given list'() {
    given:
    def violations = [ready, owner]
    def exception = new BusinessRuleViolationException(violations)

    when:
    violations.clear()

    then:
    exception.violations() == [ready, owner]
  }

  def 'the violations cannot be changed'() {
    when:
    new BusinessRuleViolationException([owner]).violations().clear()

    then:
    thrown(UnsupportedOperationException)
  }

  def 'the exception has no stack trace, cause or suppressed exceptions'() {
    given:
    def exception = new BusinessRuleViolationException([owner])

    when:
    exception.fillInStackTrace()
    exception.addSuppressed(new IllegalStateException())

    then:
    exception.stackTrace.length == 0
    exception.cause == null
    exception.suppressed.length == 0
  }

  def 'the exception needs at least one violation'() {
    when:
    new BusinessRuleViolationException([])

    then:
    def e = thrown(IllegalArgumentException)
    e.message == 'the exception needs at least one violation'
  }

  def 'the exception rejects null'() {
    when:
    new BusinessRuleViolationException(violations)

    then:
    thrown(NullPointerException)

    where:
    violations << [null, [owner, null]]
  }

  def 'Java serialization drops the internal arguments of the violations'() {
    when:
    def copy = Serialization.roundTrip(new BusinessRuleViolationException([owner, ready]))

    then:
    copy.message == '2 business rule violations: request.not-owner, task.ready'
    copy.violations()*.code() == [
      'request.not-owner',
      'task.ready'
    ]
    copy.violations()*.clientArgs() == [
      [requestNo: 'TR-1047'],
      [currentStatus: 'DRAFT']
    ]
    copy.violations().every { it.internalArgs().isEmpty() }
    copy.stackTrace.length == 0
  }

  def 'an exception with violations without arguments #how'() {
    given:
    def empty = WITHOUT_ARGUMENTS[0].clientArgs()

    when:
    def copy = copied()

    then:
    copy.violations() == WITHOUT_ARGUMENTS
    copy.violations()*.clientArgs().every { it.is(empty) }
    copy.violations()*.internalArgs().every { it.is(empty) }

    where: 'the released 0.1.0 wrote the file; it pins the serialized form, so it is never written anew'
    how                       | copied
    'reads as 0.1.0 wrote it' | {
      Serialization.read(BusinessRuleViolationExceptionSpec.getResourceAsStream('exception-with-violations-without-arguments-0.1.0.ser').bytes)
    }
    'round trips'             | {
      Serialization.roundTrip(new BusinessRuleViolationException(WITHOUT_ARGUMENTS))
    }
  }

  def 'a deserialized exception cannot have #what'() {
    when:
    Serialization.roundTrip(withViolations(violations))

    then:
    def e = thrown(InvalidObjectException)
    e.message == message

    where:
    what                    | violations || message
    'no violations'         | [] || 'the exception needs at least one violation'
    'a null list'           | null || 'violations'
    'a null violation'      | [owner, null] || null
  }

  def 'a deserialized exception keeps its violations in a list that cannot be changed'() {
    given:
    def copy = Serialization.roundTrip(withViolations(new ArrayList([owner])))

    when:
    copy.violations().add(ready)

    then:
    thrown(UnsupportedOperationException)
    copy.violations()*.code() == ['request.not-owner']
  }

  /** An exception whose violations field holds what a crafted stream could put there. */
  private BusinessRuleViolationException withViolations(Object violations) {
    def exception = new BusinessRuleViolationException([owner])
    def field = BusinessRuleViolationException.getDeclaredField('violations')
    field.accessible = true
    field.set(exception, violations)
    exception
  }

  private static String messageFor(List<Violation> violations) {
    new BusinessRuleViolationException(violations).message
  }
}
