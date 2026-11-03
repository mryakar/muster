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

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.Combinators
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide

/**
 * The order of the rules of a chain, for graphs of rules that require each other without a cycle:
 * each rule after the rules it requires, the same order whatever order the rules come in, and the
 * order that {@link me.yakar.muster.RuleChain} describes for any rules listed.
 */
class RuleGraphProperties {

  // Each rule needs a class of its own; the graph only uses it as a key.
  static final List<Class<?>> CLASSES = [String, Integer, Long, Short, Byte, Double, Float, Character]

  /**
   * A graph: each rule may require the rules before it in the list, which rules out cycles. Made
   * for the largest size and cut, since an arbitrary that depends on the size is built anew for
   * every try.
   */
  @Provide
  Arbitrary<List<RuleDefinition>> graphs() {
    def sizes = Arbitraries.integers().between(1, CLASSES.size())
    def ids = Arbitraries.strings().withCharRange('a' as char, 'e' as char).ofLength(3)
        .list().ofSize(CLASSES.size()).uniqueElements()
    def masks = Arbitraries.integers().between(0, (1 << CLASSES.size()) - 1).list().ofSize(CLASSES.size())
    Combinators.combine(sizes, ids, masks).as { size, idList, maskList ->
      (0..<size).collect { i ->
        def requires = (0..<i).findAll { j -> (maskList[i] >> j) & 1 }.collect { CLASSES[it] }
        new RuleDefinition(CLASSES[i], idList[i], Object, requires, null)
      }
    }
  }

  /** The positions of the listed rules, in the order they are listed; those beyond a graph drop. */
  @Provide
  Arbitrary<List<Integer>> listings() {
    Arbitraries.integers().between(0, CLASSES.size() - 1).list().uniqueElements().ofMaxSize(CLASSES.size())
  }

  @Property
  void 'each rule comes after the rules it requires'(
      @ForAll('graphs') List<RuleDefinition> rules, @ForAll('listings') List<Integer> listing) {
    def ordered = RuleGraph.order(rules, listed(rules, listing))
    assert ordered.size() == rules.size()
    assert ordered as Set == rules as Set
    ordered.eachWithIndex { rule, index ->
      rule.requires().each { required ->
        assert ordered.findIndexOf { it.ruleClass() == required } <index
      }
    }
  }

  @Property
  void 'the order does not depend on the order the rules come in'(
      @ForAll('graphs') List<RuleDefinition> rules, @ForAll('listings') List<Integer> listing,
      @ForAll Random random) {
    def shuffled = new ArrayList<>(rules)
    Collections.shuffle(shuffled, random)
    def listed = listed(rules, listing)
    assert RuleGraph.order(shuffled, listed) == RuleGraph.order(rules, listed)
  }

  @Property
  void 'the rules run in the order a chain describes'(
      @ForAll('graphs') List<RuleDefinition> rules, @ForAll('listings') List<Integer> listing) {
    def listed = listed(rules, listing)
    assert RuleGraph.order(rules, listed)*.ruleClass() == describedOrder(rules, listed)
  }

  private static List<Class<?>> listed(List<RuleDefinition> rules, List<Integer> listing) {
    listing.findAll { it <rules.size() }.collect { rules[it].ruleClass() }
  }

  /**
   * The order as a chain describes it, step by step: each listed rule in list order, before it the
   * rules it requires that have not run yet, each after its own requirements, listed ones in list
   * order and the others by id; then the rules not listed, each after the rules it requires, by id.
   */
  private static List<Class<?>> describedOrder(List<RuleDefinition> rules, List<Class<?>> listed) {
    Map<Class<?>, RuleDefinition> byClass = rules.collectEntries { [it.ruleClass(), it] }
    def position = { Class<?> ruleClass ->
      listed.contains(ruleClass) ? listed.indexOf(ruleClass) : Integer.MAX_VALUE
    }
    List<Class<?>> run = []
    Closure runAfterRequired
    runAfterRequired = { Class<?> ruleClass ->
      if (!run.contains(ruleClass)) {
        byClass[ruleClass].requires()
            .sort(false) { a, b ->
              position(a) <=> position(b) ?: byClass[a].id() <=> byClass[b].id()
            }
            .each { runAfterRequired(it) }
        run << ruleClass
      }
    }
    listed.each { runAfterRequired(it) }
    def rest = rules.findAll { !run.contains(it.ruleClass()) }
    while (rest) {
      def next = rest.findAll { rule -> run.containsAll(rule.requires()) }.min { it.id() }
      run << next.ruleClass()
      rest.remove(next)
    }
    run
  }
}
