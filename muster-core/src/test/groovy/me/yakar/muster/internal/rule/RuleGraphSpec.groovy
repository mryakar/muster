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
package me.yakar.muster.internal.rule

import spock.lang.Specification

class RuleGraphSpec extends Specification {

  // Each rule needs a class of its own; the graph only uses it as a key.
  static class A {}
  static class B {}
  static class C {}
  static class D {}
  static class E {}

  static RuleDefinition rule(Class<?> ruleClass, String id, List<Class<?>> requires = [], Class<?> inputType = Object) {
    new RuleDefinition(ruleClass, id, inputType, requires)
  }

  static Map<Class<?>, RuleDefinition> byClass(RuleDefinition... rules) {
    rules.collectEntries { [it.ruleClass(), it] }
  }

  static List<RuleDefinition> order(List<RuleDefinition> rules, List<Class<?>> listed = []) {
    RuleGraph.order(rules, listed)
  }

  static void validate(Map<Class<?>, RuleDefinition> rules, Set<Class<?>> disabled = [] as Set) {
    RuleGraph.validate(rules, disabled)
  }

  def 'rules that do not depend on each other come in the order of their ids'() {
    expect:
    order([
      rule(A, 'b'),
      rule(B, 'c'),
      rule(C, 'a')
    ])*.id() == ['a', 'b', 'c']
  }

  def 'a rule comes after the rules it requires, whatever its id'() {
    expect:
    order([
      rule(A, 'task.assignable', [B]),
      rule(B, 'task.exists')
    ])*.id() == [
      'task.exists',
      'task.assignable'
    ]
  }

  def 'a rule that becomes ready waits for ready rules with lower ids'() {
    expect:
    order([
      rule(A, 'b', [B]),
      rule(B, 'x'),
      rule(C, 'c')
    ])*.id() == ['c', 'x', 'b']
  }

  def 'listed rules that do not depend on each other come in the order they are listed'() {
    expect:
    order([
      rule(A, 'b'),
      rule(B, 'c'),
      rule(C, 'a')
    ], [B, C, A])*.id() == ['c', 'a', 'b']
  }

  def 'a rule that is not listed comes just before the first listed rule that requires it'() {
    expect:
    order([
      rule(A, 'name-unique', [C]),
      rule(B, 'plate-format'),
      rule(C, 'vehicle.exists')
    ], [A, B])*.id() == [
      'vehicle.exists',
      'name-unique',
      'plate-format'
    ]
  }

  def 'a rule that is not listed comes just before the first listed rule that requires it through other rules'() {
    expect:
    order([
      rule(A, 'started', [B]),
      rule(B, 'ready', [C]),
      rule(C, 'exists'),
      rule(D, 'audited')
    ], [A, D])*.id() == [
      'exists',
      'ready',
      'started',
      'audited'
    ]
  }

  def 'a listed rule that an earlier listed rule requires comes before it'() {
    expect:
    order([
      rule(A, 'assignable', [B]),
      rule(B, 'exists'),
      rule(C, 'audited')
    ], [A, C, B])*.id() == [
      'exists',
      'assignable',
      'audited'
    ]
  }

  def 'listed rules that a listed rule requires come before it in the order they are listed'() {
    expect:
    order([
      rule(A, 'closable', [C, B]),
      rule(B, 'locked'),
      rule(C, 'exists')
    ], [A, B, C])*.id() == [
      'locked',
      'exists',
      'closable'
    ]
  }

  def 'of the rules a listed rule requires, listed ones come first, then the others in the order of their ids'() {
    expect:
    order([
      rule(A, 'closable', [B, C, D]),
      rule(B, 'locked'),
      rule(C, 'audit'),
      rule(D, 'exists'),
      rule(E, 'archived')
    ], [A, E, D])*.id() == [
      'exists',
      'audit',
      'locked',
      'closable',
      'archived'
    ]
  }

  def 'a required listed rule brings its own requirements just before it'() {
    expect:
    order([
      rule(A, 'top', [B, D]),
      rule(B, 'second', [C]),
      rule(C, 'a-unlisted'),
      rule(D, 'third')
    ], [A, B, D])*.id() == [
      'a-unlisted',
      'second',
      'third',
      'top'
    ]
  }

  def 'a required rule brings its own requirements just before it, also through rules that are not listed'() {
    expect:
    order([
      rule(A, 'listed', [B, C]),
      rule(B, 'p'),
      rule(C, 'q', [D]),
      rule(D, 'a')
    ], [A])*.id() == ['p', 'a', 'q', 'listed']
  }

  def 'rules that no listed rule requires come after the listed ones, after their requirements, in the order of their ids'() {
    expect:
    order([
      rule(A, 'listed', [B]),
      rule(B, 'needed'),
      rule(C, 'z-bound', [D]),
      rule(D, 'y-bound-needed'),
      rule(E, 'a-bound', [B])
    ], [A])*.id() == [
      'needed',
      'listed',
      'a-bound',
      'y-bound-needed',
      'z-bound'
    ]
  }

  def 'a rule that no listed rule requires comes after its requirements, whatever their ids'() {
    expect:
    order([
      rule(A, 'z-listed'),
      rule(B, 'a-tail', [C]),
      rule(C, 'b-tail-needed')
    ], [A])*.id() == [
      'z-listed',
      'b-tail-needed',
      'a-tail'
    ]
  }

  def 'a rule that requires several rules comes after all of them'() {
    expect:
    order([
      rule(A, 'top', [B, C]),
      rule(B, 'left', [D]),
      rule(C, 'right', [D]),
      rule(D, 'base'),
      rule(E, 'alpha')
    ])*.id() == [
      'alpha',
      'base',
      'left',
      'right',
      'top'
    ]
  }

  def 'a graph without cycles, with a rule required twice over, is valid'() {
    when:
    validate(byClass(rule(A, 'top', [B, C]), rule(B, 'left', [D]), rule(C, 'right', [D]), rule(D, 'base')))

    then:
    noExceptionThrown()
  }

  def 'a required rule must be registered'() {
    when:
    validate(byClass(rule(A, 'task.ready', [B])))

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "${A.name} requires ${B.name}, which is not registered"
  }

  def 'a required rule must accept the input of the rule that requires it'() {
    when:
    validate(byClass(rule(A, 'task.ready', [B], Long), rule(B, 'task.exists', [], inputType)))

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "${A.name} requires ${B.name}, which checks ${inputType.typeName}, not java.lang.Long"

    where:
    inputType << [String, Integer]
  }

  def 'a required rule may accept a wider input'() {
    when:
    validate(byClass(rule(A, 'task.ready', [B], Long), rule(B, 'task.exists', [], Number)))

    then:
    noExceptionThrown()
  }

  def 'an enabled rule cannot require a disabled rule'() {
    when:
    validate(byClass(rule(A, 'task.ready', [B]), rule(B, 'task.exists')), [B] as Set)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "rules that require disabled rules are enabled: 'task.ready' requires 'task.exists'; disable them as well, or enable what they require"
  }

  def 'every enabled rule that requires a disabled rule through other rules is named at once'() {
    when:
    validate(byClass(rule(A, 'task.started', [B]), rule(B, 'task.ready', [C]), rule(C, 'task.exists'), rule(D, 'task.deadline', [C])), [C] as Set)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "rules that require disabled rules are enabled: 'task.deadline', 'task.ready', 'task.started' require 'task.exists'; disable them as well, or enable what they require"
  }

  def 'the rules that require each disabled rule are named for each of them'() {
    when:
    validate(byClass(rule(A, 'vehicle.name-unique', [B]), rule(B, 'vehicle.exists'), rule(C, 'task.ready', [D]), rule(D, 'task.exists')), [B, D] as Set)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "rules that require disabled rules are enabled: 'task.ready' requires 'task.exists'; 'vehicle.name-unique' requires 'vehicle.exists'; disable them as well, or enable what they require"
  }

  def 'an enabled rule that requires a chain of disabled rules is named for each of them'() {
    when:
    validate(byClass(rule(A, 'task.started', [B]), rule(B, 'task.ready', [C]), rule(C, 'task.exists')), [B, C] as Set)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "rules that require disabled rules are enabled: 'task.started' requires 'task.exists'; 'task.started' requires 'task.ready'; disable them as well, or enable what they require"
  }

  def 'a rule reached along two paths does not make an unrelated disabled rule a requirement'() {
    when:
    validate(byClass(rule(A, 'task.started', [B, C]), rule(B, 'task.ready', [D]), rule(C, 'task.assigned', [D]), rule(D, 'task.exists'), rule(E, 'vehicle.exists')), [E] as Set)

    then:
    noExceptionThrown()
  }

  def 'a disabled rule may require a disabled or an enabled rule'() {
    when:
    validate(byClass(rule(A, 'task.started', [B]), rule(B, 'task.ready', [C]), rule(C, 'task.exists')), [A, B] as Set)

    then:
    noExceptionThrown()
  }

  def 'rules cannot require each other in a cycle: #edges'() {
    when:
    validate(graph(edges))

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "rules require each other in a cycle: $cycle"

    where:
    edges || cycle
    'a -> a' || 'a -> a'
    'a -> b, b -> a' || 'a -> b -> a'
    'b -> a, a -> b' || 'a -> b -> a'
    'entry -> x, x -> y, y -> z, z -> x' || 'x -> y -> z -> x'
  }

  /**
   * Builds a graph from edges such as {@code 'a -> b'}, read as "a requires b". Rules are registered
   * in the order they first appear.
   */
  static Map<Class<?>, RuleDefinition> graph(String edges) {
    def ids = []
    def requires = [:].withDefault { [] }
    edges.split(',')*.trim().each { edge ->
      def (from, to) = edge.split('->')*.trim()
      [from, to].each { if (!(it in ids)) ids << it }
      requires[from] << to
    }
    def classOf = { [A, B, C, D, E][ids.indexOf(it)] }
    ids.collectEntries {
      [
        classOf(it),
        rule(classOf(it), it, requires[it].collect(classOf))
      ]
    }
  }
}
