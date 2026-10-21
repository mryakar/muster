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
package me.yakar.muster.internal

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

  def 'rules that do not depend on each other come in the order of their ids'() {
    expect:
    RuleGraph.order([
      rule(A, 'b'),
      rule(B, 'c'),
      rule(C, 'a')
    ])*.id() == ['a', 'b', 'c']
  }

  def 'a rule comes after the rules it requires, whatever its id'() {
    expect:
    RuleGraph.order([
      rule(A, 'task.assignable', [B]),
      rule(B, 'task.exists')
    ])*.id() == [
      'task.exists',
      'task.assignable'
    ]
  }

  def 'a rule that becomes ready waits for ready rules with lower ids'() {
    expect:
    RuleGraph.order([
      rule(A, 'b', [B]),
      rule(B, 'x'),
      rule(C, 'c')
    ])*.id() == ['c', 'x', 'b']
  }

  def 'a rule that requires several rules comes after all of them'() {
    expect:
    RuleGraph.order([
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
    RuleGraph.validate(byClass(rule(A, 'top', [B, C]), rule(B, 'left', [D]), rule(C, 'right', [D]), rule(D, 'base')))

    then:
    noExceptionThrown()
  }

  def 'a required rule must be registered'() {
    when:
    RuleGraph.validate(byClass(rule(A, 'task.ready', [B])))

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "${A.name} requires ${B.name}, which is not registered"
  }

  def 'a required rule must accept the input of the rule that requires it'() {
    when:
    RuleGraph.validate(byClass(rule(A, 'task.ready', [B], Long), rule(B, 'task.exists', [], inputType)))

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "${A.name} requires ${B.name}, which checks ${inputType.typeName}, not java.lang.Long"

    where:
    inputType << [String, Integer]
  }

  def 'a required rule may accept a wider input'() {
    when:
    RuleGraph.validate(byClass(rule(A, 'task.ready', [B], Long), rule(B, 'task.exists', [], Number)))

    then:
    noExceptionThrown()
  }

  def 'rules cannot require each other in a cycle: #cycle'() {
    when:
    RuleGraph.validate(byClass(*rules))

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "rules require each other in a cycle: $cycle"

    where:
    rules || cycle
    [rule(A, 'a', [A])] || 'a → a'
    [
      rule(A, 'a', [B]),
      rule(B, 'b', [A])
    ] || 'a → b → a'
    [
      rule(B, 'b', [A]),
      rule(A, 'a', [B])
    ] || 'a → b → a'
    [
      rule(A, 'entry', [B]),
      rule(B, 'x', [C]),
      rule(C, 'y', [D]),
      rule(D, 'z', [B])
    ] || 'x → y → z → x'
  }
}
