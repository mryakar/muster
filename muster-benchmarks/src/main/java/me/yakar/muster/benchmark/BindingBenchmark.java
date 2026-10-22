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
import me.yakar.muster.rule.BusinessRule;
import me.yakar.muster.rule.Rule;
import me.yakar.muster.rule.RuleContext;
import me.yakar.muster.rule.RuleResult;
import me.yakar.muster.rule.RuleTarget;
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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * {@code muster.check(target)} from every thread of the machine at once, for classes that are
 * already bound. Muster keeps the chain of each class in a hash map; two classes whose hashes fall
 * into the same bucket of it are bound one after the other, and the benchmark checks the first or
 * the second of them. Which classes share a bucket depends on their identity hashes, so it changes
 * from run to run; with seventeen classes and sixteen buckets, two always do. The buckets are those
 * of a ConcurrentHashMap of default size, as Muster's map of chains is; should that change, so must
 * {@link #bucket}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@Threads(Threads.MAX)
public class BindingBenchmark {

  /** Which of the two classes that share a bucket is checked: the one bound first or second. */
  @Param({"first", "second"})
  public String bound;

  private Muster muster;
  private RuleTarget target;

  /** Binds two classes that share a bucket, in order. */
  @Setup
  public void setUp() {
    muster = Muster.builder().rules(new AnyTarget()).build();
    var first = (RuleTarget) null;
    var second = (RuleTarget) null;
    for (var i = 0; i < TARGETS.size() && second == null; i++) {
      for (var j = i + 1; j < TARGETS.size() && second == null; j++) {
        if (bucket(TARGETS.get(i)) == bucket(TARGETS.get(j))) {
          first = TARGETS.get(i);
          second = TARGETS.get(j);
        }
      }
    }
    if (first == null || second == null) {
      throw new IllegalStateException("no two classes share a bucket");
    }
    muster.check(first);
    muster.check(second);
    target = bound.equals("first") ? first : second;
  }

  /** Checks a target whose class is already bound. */
  @Benchmark
  public void check() {
    muster.check(target);
  }

  /** The bucket of a class in a map of sixteen, as ConcurrentHashMap spreads hashes. */
  private static int bucket(RuleTarget target) {
    var hash = target.getClass().hashCode();
    return (hash ^ (hash >>> 16)) & 0x7fffffff & 15;
  }

  /** A rule that every target passes. */
  @Rule(id = "target.any")
  static final class AnyTarget implements BusinessRule<RuleTarget> {
    @Override
    public RuleResult check(RuleTarget input, RuleContext ctx) {
      return RuleResult.passed();
    }
  }

  record Target00() implements RuleTarget {}

  record Target01() implements RuleTarget {}

  record Target02() implements RuleTarget {}

  record Target03() implements RuleTarget {}

  record Target04() implements RuleTarget {}

  record Target05() implements RuleTarget {}

  record Target06() implements RuleTarget {}

  record Target07() implements RuleTarget {}

  record Target08() implements RuleTarget {}

  record Target09() implements RuleTarget {}

  record Target10() implements RuleTarget {}

  record Target11() implements RuleTarget {}

  record Target12() implements RuleTarget {}

  record Target13() implements RuleTarget {}

  record Target14() implements RuleTarget {}

  record Target15() implements RuleTarget {}

  record Target16() implements RuleTarget {}

  private static final List<RuleTarget> TARGETS =
      List.of(
          new Target00(),
          new Target01(),
          new Target02(),
          new Target03(),
          new Target04(),
          new Target05(),
          new Target06(),
          new Target07(),
          new Target08(),
          new Target09(),
          new Target10(),
          new Target11(),
          new Target12(),
          new Target13(),
          new Target14(),
          new Target15(),
          new Target16());
}
