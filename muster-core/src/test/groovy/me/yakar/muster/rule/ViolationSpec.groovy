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
package me.yakar.muster.rule

import java.io.InvalidObjectException
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import me.yakar.muster.Serialization
import me.yakar.muster.internal.rule.RuleIds
import spock.lang.Shared
import spock.lang.Specification

class ViolationSpec extends Specification {

  @Shared
  def context = RuleContext.forRule('vehicle.name-unique')

  def 'a violation without a code uses the rule id as its code'() {
    when:
    def violation = context.violation().build()

    then:
    violation.ruleId() == 'vehicle.name-unique'
    violation.code() == 'vehicle.name-unique'
    violation.clientArgs().isEmpty()
    violation.internalArgs().isEmpty()
    violation.target().isEmpty()
    violation.category().isEmpty()
    violation.severity() == Severity.ERROR
  }

  def 'every character that a rule id may have is one that a code may have, so the rule id needs no second check as a code'() {
    given:
    def accepted = (Character.MIN_VALUE..Character.MAX_VALUE).collect {
      "a${it as char}a".toString()
    }.findAll {
      RuleIds.isValid(it)
    }

    expect:
    accepted.size() == 38
    accepted.every { Checks.requireKey(it, 'code') == it }
  }

  def 'a rule can choose another code'() {
    when:
    def violation = context.violation('vehicle.name-taken').build()

    then:
    violation.ruleId() == 'vehicle.name-unique'
    violation.code() == 'vehicle.name-taken'
  }

  def 'client and internal arguments are kept apart'() {
    when:
    def violation = context.violation()
        .clientArg('name', 'Truck 1')
        .internalArg('conflictingVehicleId', 42L)
        .build()

    then:
    violation.clientArgs() == [name: 'Truck 1']
    violation.internalArgs() == [conflictingVehicleId: 42L]
  }

  def 'arguments keep the order they were added in'() {
    when:
    def violation = context.violation()
        .clientArg('c', 1).clientArg('a', 2).clientArg('b', 3)
        .internalArg('z', 1).internalArg('x', 2).internalArg('y', 3)
        .build()

    then:
    violation.clientArgs().keySet() as List == ['c', 'a', 'b']
    violation.internalArgs().keySet() as List == ['z', 'x', 'y']
  }

  def 'arguments cannot be changed after the violation is built'() {
    given:
    def violation = context.violation().clientArg('a', 1).internalArg('b', 2).build()

    when:
    violation.clientArgs().put('c', 3)

    then:
    thrown(UnsupportedOperationException)

    when:
    violation.internalArgs().put('c', 3)

    then:
    thrown(UnsupportedOperationException)
  }

  def 'changing the builder later does not change a violation already built'() {
    given:
    def builder = context.violation().clientArg('a', 1)
    def first = builder.build()

    when:
    builder.clientArg('b', 2).internalArg('c', 3).target('name').category(Category.CONFLICT)

    then:
    first.clientArgs() == [a: 1]
    first.internalArgs().isEmpty()
    first.target().isEmpty()
    first.category().isEmpty()
  }

  def 'an argument name can be used only once across both kinds: #first, then #second'() {
    when:
    def builder = context.violation()
    builder."$first"('id', 1)
    builder."$second"('id', 2)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == message

    where:
    first         | second || message
    'clientArg'   | 'clientArg' || "'id' is already a client argument"
    'clientArg'   | 'internalArg' || "'id' is already a client argument"
    'internalArg' | 'internalArg' || "'id' is already an internal argument"
    'internalArg' | 'clientArg' || "'id' is already an internal argument"
  }

  def 'an argument needs a name and a value: #method(#name, #value)'() {
    when:
    context.violation()."$method"(name, value)

    then:
    def e = thrown(exception)
    e.message == message

    where:
    method        | name      | value || exception                | message
    'clientArg'   | ' '       | 1 || IllegalArgumentException | 'argument name must not be blank'
    'internalArg' | ''        | 1 || IllegalArgumentException | 'argument name must not be blank'
    'clientArg'   | null      | 1 || NullPointerException     | 'argument name'
    'clientArg'   | 'name'    | null || NullPointerException     | "value of argument 'name'"
    'internalArg' | 'ownerId' | null || NullPointerException     | "value of argument 'ownerId'"
  }

  def 'an argument name cannot contain #kind: #method'() {
    when:
    context.violation()."$method"(name, 1)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "argument name '$name' contains $character; it cannot contain whitespace, spaces, control or format characters, or unpaired surrogates"

    where:
    kind                         | method        | name || character
    'a space'                    | 'clientArg'   | 'vehicle name' || 'U+0020'
    'a space'                    | 'internalArg' | 'vehicle name' || 'U+0020'
    'a no-break space'           | 'clientArg'   | 'vehicle\u00a0name' || 'U+00A0'
    'a trailing newline'         | 'clientArg'   | 'name\n' || 'U+000A'
    'a zero-width space'         | 'clientArg'   | 'vehicle\u200bname' || 'U+200B'
    'an unpaired high surrogate' | 'internalArg' | 'name\uD83D' || 'U+D83D'
  }

  def 'an argument name cannot contain a brace: #method(#name)'() {
    when:
    context.violation()."$method"(name, 1)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "argument name '$name' contains $character; it cannot contain braces, which mark arguments in message templates"

    where:
    method        | name || character
    'clientArg'   | '{name}' || 'U+007B'
    'clientArg'   | 'na}me' || 'U+007D'
    'internalArg' | 'name{' || 'U+007B'
    'internalArg' | '}' || 'U+007D'
  }

  def 'an argument name can be #kind'() {
    expect:
    context.violation().clientArg(name, 1).build().clientArgs().keySet() == [name] as Set

    where:
    kind                      | name
    'camel case'              | 'conflictingVehicleId'
    'dotted'                  | 'vehicle.name'
    'outside the basic plane' | 'name🚚'
  }

  def 'a violation can name a target and a category'() {
    when:
    def violation = context.violation().target('name').category(Category.CONFLICT).build()

    then:
    violation.target() == Optional.of('name')
    violation.category() == Optional.of(Category.CONFLICT)
  }

  def 'target and category are checked: #method(#value)'() {
    when:
    context.violation()."$method"(value)

    then:
    def e = thrown(exception)
    e.message == message

    where:
    method     | value || exception                | message
    'target'   | ' ' || IllegalArgumentException | 'target must not be blank'
    'target'   | null || NullPointerException     | 'target'
    'category' | null || NullPointerException     | 'category'
  }

  def 'a code must not be blank'() {
    when:
    context.violation(code)

    then:
    def e = thrown(exception)
    e.message == message

    where:
    code || exception                | message
    ' ' || IllegalArgumentException | 'code must not be blank'
    null || NullPointerException     | 'code'
  }

  def 'a code cannot contain #kind'() {
    when:
    context.violation(code)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "code '$code' contains $character; it cannot contain whitespace, spaces, control or format characters, or unpaired surrogates"

    where:
    kind                        | code || character
    'a space'                   | 'name taken' || 'U+0020'
    'a tab'                     | 'name\ttaken' || 'U+0009'
    'a no-break space'          | 'name\u00a0taken' || 'U+00A0'
    'an ideographic space'      | 'name\u3000taken' || 'U+3000'
    'a line separator'          | 'name\u2028taken' || 'U+2028'
    'a control character'       | 'name\u0000taken' || 'U+0000'
    'a trailing newline'        | 'name-taken\n' || 'U+000A'
    'a zero-width space'        | 'name\u200btaken' || 'U+200B'
    'a byte order mark'         | '\ufeffname-taken' || 'U+FEFF'
    'a soft hyphen'             | 'name\u00adtaken' || 'U+00AD'
    'a word joiner'             | 'name\u2060taken' || 'U+2060'
    'an unpaired high surrogate'| 'name\uD83Dtaken' || 'U+D83D'
    'an unpaired low surrogate' | 'name-taken\uDE9A' || 'U+DE9A'
  }

  def 'a code can be #kind'() {
    expect:
    context.violation(code).build().code() == code

    where:
    kind                          | code
    'a rule id'                   | 'vehicle.name-taken'
    'in capitals with underscores'| 'VEHICLE_NAME_TAKEN'
    'outside the basic plane'     | 'name\uD83D\uDE9Ataken'
    'with braces'                 | '{vehicle.name-taken}'
  }

  def 'violations with the same content are equal'() {
    expect:
    full().build() == full().build()
    full().build().hashCode() == full().build().hashCode()
    context.violation().build() == context.violation().build()
  }

  def 'violations differing in #difference are not equal'() {
    expect:
    full().build() != other
    full().build().hashCode() != other.hashCode()

    where:
    difference          | other
    'rule id'           | RuleContext.forRule('other').violation('vehicle.name-taken').clientArg('name', 'Truck 1').internalArg('id', 42L).target('name').category(Category.CONFLICT).build()
    'code'              | RuleContext.forRule('vehicle.name-unique').violation('other').clientArg('name', 'Truck 1').internalArg('id', 42L).target('name').category(Category.CONFLICT).build()
    'client arguments'  | RuleContext.forRule('vehicle.name-unique').violation('vehicle.name-taken').clientArg('name', 'Truck 2').internalArg('id', 42L).target('name').category(Category.CONFLICT).build()
    'internal args'     | RuleContext.forRule('vehicle.name-unique').violation('vehicle.name-taken').clientArg('name', 'Truck 1').internalArg('id', 43L).target('name').category(Category.CONFLICT).build()
    'target'            | RuleContext.forRule('vehicle.name-unique').violation('vehicle.name-taken').clientArg('name', 'Truck 1').internalArg('id', 42L).target('code').category(Category.CONFLICT).build()
    'category'          | RuleContext.forRule('vehicle.name-unique').violation('vehicle.name-taken').clientArg('name', 'Truck 1').internalArg('id', 42L).target('name').category(Category.NOT_FOUND).build()
  }

  def 'a violation is not equal to null or to another type'() {
    given:
    def violation = full().build()

    expect:
    !violation.equals(null)
    !violation.equals('vehicle.name-taken')
    violation.equals(violation)
  }

  def 'toString shows internal arguments for developers'() {
    expect:
    full().build().toString() == 'Violation[ruleId=vehicle.name-unique, code=vehicle.name-taken, ' +
        'clientArgs={name=Truck 1}, internalArgs={id=42}, target=name, category=CONFLICT, severity=ERROR]'
    context.violation().build().toString() == 'Violation[ruleId=vehicle.name-unique, ' +
        'code=vehicle.name-unique, clientArgs={}, internalArgs={}, severity=ERROR]'
  }

  def 'Java serialization drops internal arguments and keeps everything else'() {
    when:
    def copy = Serialization.roundTrip(original)

    then:
    copy.ruleId() == original.ruleId()
    copy.code() == original.code()
    copy.clientArgs() == original.clientArgs()
    copy.internalArgs().isEmpty()
    copy.target() == original.target()
    copy.category() == original.category()

    where:
    original << [
      full().build(),
      context.violation().internalArg('id', 42L).build()
    ]
  }

  def 'a deserialized violation cannot have #what'() {
    given: 'a violation whose field holds what a crafted stream could put there'
    def violation = full().build()
    def field = Violation.getDeclaredField(name)
    field.accessible = true
    field.set(violation, value)

    when:
    Serialization.roundTrip(violation)

    then:
    def e = thrown(InvalidObjectException)
    e.message == message
    e.cause.message == message

    where:
    what                       | name         | value || message
    'an invalid rule id'       | 'ruleId'     | 'Not An Id' || "the rule id 'Not An Id' is invalid; an id starts with a lowercase letter and continues with lowercase letters, digits, and single dots or hyphens between them, as in 'vehicle.name-unique'"
    'no rule id'               | 'ruleId'     | null || 'ruleId'
    'an invalid code'          | 'code'       | 'name taken' || "code 'name taken' contains U+0020; it cannot contain whitespace, spaces, control or format characters, or unpaired surrogates"
    'an invalid argument name' | 'clientArgs' | new LinkedHashMap(['{name}': 'Truck 1']) || "argument name '{name}' contains U+007B; it cannot contain braces, which mark arguments in message templates"
    'a blank target'           | 'target'     | ' ' || 'target must not be blank'
  }

  def 'arguments of a deserialized violation keep their order and cannot be changed'() {
    when:
    def copy = Serialization.roundTrip(context.violation().clientArg('c', 1).clientArg('a', 2).build())
    copy.clientArgs().put('b', 3)

    then:
    copy.clientArgs().keySet() as List == ['c', 'a']
    thrown(UnsupportedOperationException)
  }

  def 'violations without arguments share one empty map, also after Java serialization'() {
    given:
    def empty = context.violation().build().clientArgs()

    expect:
    context.violation().build().clientArgs().is(empty)
    context.violation().build().internalArgs().is(empty)
    context.violation().clientArg('a', 1).build().internalArgs().is(empty)
    context.violation().internalArg('a', 1).build().clientArgs().is(empty)
    Serialization.roundTrip(context.violation().build()).clientArgs().is(empty)
    Serialization.roundTrip(context.violation().internalArg('a', 1).build()).internalArgs().is(empty)
  }

  def 'a builder that has had no arguments gives each violation the arguments it has when it builds'() {
    given:
    def builder = context.violation()
    def first = builder.build()

    when:
    def second = builder.clientArg('a', 1).build()
    def third = builder.internalArg('b', 2).build()

    then:
    first.clientArgs().isEmpty()
    first.internalArgs().isEmpty()
    second.clientArgs() == [a: 1]
    second.internalArgs().isEmpty()
    third.clientArgs() == [a: 1]
    third.internalArgs() == [b: 2]
  }

  def 'violations built on many threads at once share the empty map and equal each other'() {
    given:
    def threads = 16
    def start = new CountDownLatch(1)
    def pool = Executors.newFixedThreadPool(threads)
    def tasks = (1..threads).collect { {
        ->
        start.await()
        (1..200).collect { context.violation().build() }
      } as Callable<List<Violation>>
    }

    when:
    def futures = tasks.collect { pool.submit(it) }
    start.countDown()
    def violations = futures.collectMany { it.get(1, TimeUnit.MINUTES) }

    then:
    def empty = context.violation().build().clientArgs()
    violations.every {
      it.clientArgs().is(empty) && it.internalArgs().is(empty)
    }
    violations.every { it == context.violation().build() }

    cleanup:
    pool.shutdownNow()
  }

  def 'a violation #what reads as 0.1.0 wrote it, and is written the same way'() {
    given:
    def written = ViolationSpec.getResourceAsStream("violation-${what.replace(' ', '-')}-0.1.0.ser").bytes

    expect:
    Serialization.read(written) == expected
    Serialization.write(original) == written

    where:
    what                | original || expected
    'without arguments' | context.violation().build() || context.violation().build()
    'with arguments'    | full().build() || context.violation('vehicle.name-taken').clientArg('name', 'Truck 1').target('name').category(Category.CONFLICT).build()
  }

  private Violation.Builder full() {
    context.violation('vehicle.name-taken')
        .clientArg('name', 'Truck 1')
        .internalArg('id', 42L)
        .target('name')
        .category(Category.CONFLICT)
  }
}
