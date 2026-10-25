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
package me.yakar.muster.benchmark;

import java.util.List;
import java.util.concurrent.TimeUnit;
import me.yakar.muster.Muster;
import me.yakar.muster.RuleChain;
import me.yakar.muster.benchmark.Rules.IndexedRule;
import me.yakar.muster.rule.BusinessRule;
import me.yakar.muster.rule.Rule;
import me.yakar.muster.rule.RuleContext;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * A check that passes, against a growing number of rules. {@code direct} calls the rules by hand
 * and is the baseline: the difference to it is what Muster adds.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(5)
public class CheckBenchmark {

  /** How many rules the input is checked against. */
  @Param({"0", "1", "5", "20"})
  public int rules;

  private List<IndexedRule> selected;
  private List<RuleContext> contexts;
  private RuleChain<Order> chain;
  private Muster muster;
  private Order order;

  /** Prepares Muster and a chain once, as an application does when it starts. */
  @Setup
  public void setUp() {
    selected = Rules.first(rules);
    contexts = selected.stream().map(rule -> RuleContext.forRule(id(rule))).toList();
    muster = Muster.builder().rules(selected.toArray(BusinessRule<?>[]::new)).build();
    chain = muster.chain(Order.class, Rules.classes(selected));
    order = new Order(0);
  }

  /**
   * Calls the rules by hand.
   *
   * @param blackhole keeps the results from being optimized away
   */
  @Benchmark
  public void direct(Blackhole blackhole) {
    for (var i = 0; i < selected.size(); i++) {
      blackhole.consume(selected.get(i).check(order, contexts.get(i)));
    }
  }

  /** Checks the input with a chain prepared for its type. */
  @Benchmark
  public void chain() {
    chain.check(order);
  }

  /** Checks the input with the rules that bind to its class. */
  @Benchmark
  public void target() {
    muster.check(order);
  }

  private static String id(IndexedRule rule) {
    return rule.getClass().getAnnotation(Rule.class).id();
  }
}
