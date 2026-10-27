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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import me.yakar.muster.Muster;
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
 * What checking a collection costs, with twenty rules: {@code muster.checkAll} on the collection,
 * {@code muster.check} on each element, and the rules called by hand on each element.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(5)
public class CollectionBenchmark {

  /** Muster's logger, held so that the level set below outlives a garbage collection. */
  private static final Logger MUSTER_LOGGER = Logger.getLogger("me.yakar.muster");

  /** How many elements the collection holds. */
  @Param({"1", "10", "100"})
  public int elements;

  private List<Rules.IndexedRule> selected;
  private List<RuleContext> contexts;
  private Muster muster;
  private List<Order> orders;

  /** Prepares Muster with twenty rules and a collection of passing orders. */
  @Setup
  public void setUp() {
    // The rules check element by element, which Muster warns about once for each; here that is
    // intended.
    MUSTER_LOGGER.setLevel(Level.SEVERE);
    selected = Rules.first(20);
    contexts = selected.stream().map(rule -> RuleContext.forRule(id(rule))).toList();
    muster = Muster.builder().rules(selected.toArray(BusinessRule<?>[]::new)).build();
    orders = IntStream.range(0, elements).mapToObj(i -> new Order(0)).toList();
  }

  /** Checks the collection in one call. */
  @Benchmark
  public void checkAll() {
    muster.checkAll(orders);
  }

  /** Checks each element in a call of its own. */
  @Benchmark
  public void eachCheck() {
    for (var i = 0; i < orders.size(); i++) {
      muster.check(orders.get(i));
    }
  }

  /**
   * Calls the rules by hand on each element.
   *
   * @param blackhole keeps the results from being optimized away
   */
  @Benchmark
  public void direct(Blackhole blackhole) {
    for (var i = 0; i < orders.size(); i++) {
      var order = orders.get(i);
      for (var j = 0; j < selected.size(); j++) {
        blackhole.consume(selected.get(j).check(order, contexts.get(j)));
      }
    }
  }

  private static String id(Rules.IndexedRule rule) {
    return rule.getClass().getAnnotation(Rule.class).id();
  }
}
