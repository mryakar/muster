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

import java.util.concurrent.TimeUnit;
import me.yakar.muster.BusinessRuleViolationException;
import me.yakar.muster.CheckMode;
import me.yakar.muster.Muster;
import me.yakar.muster.RuleChain;
import me.yakar.muster.rule.BusinessRule;
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

/**
 * A check of twenty rules that fails, in each mode. The violated rules are spread evenly and the
 * last rule is always one of them, so a check in {@code COLLECT_ALL} mode runs every rule, and one
 * in {@code FAIL_FAST} mode stops at the first violated rule.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(5)
public class ViolationBenchmark {

  /** How many of the twenty rules the input violates. */
  @Param({"1", "5"})
  public int violated;

  /** The mode of the check. */
  @Param({"FAIL_FAST", "COLLECT_ALL"})
  public CheckMode mode;

  private RuleChain<Order> chain;
  private Order order;

  /** Prepares the chain once, and an input that violates evenly spread rules. */
  @Setup
  public void setUp() {
    var size = Rules.ALL.size();
    if (violated < 1 || violated > size) {
      throw new IllegalArgumentException("violated must be between 1 and " + size);
    }
    var muster = Muster.builder().rules(Rules.ALL.toArray(BusinessRule<?>[]::new)).build();
    chain = muster.chain(Order.class, Rules.classes(Rules.ALL)).withMode(mode);
    // Counted back from the last rule, so that it is always violated.
    var failing = 0L;
    for (var i = 0; i < violated; i++) {
      failing |= 1L << (size - 1 - i * size / violated);
    }
    order = new Order(failing);
  }

  /**
   * Checks the input and catches the exception, as the caller would.
   *
   * @return the exception, so that it is not optimized away
   */
  @Benchmark
  public BusinessRuleViolationException check() {
    try {
      chain.check(order);
    } catch (BusinessRuleViolationException e) {
      return e;
    }
    throw new IllegalStateException("the check passed");
  }
}
