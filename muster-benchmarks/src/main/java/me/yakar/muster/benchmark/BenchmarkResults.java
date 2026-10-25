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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.options.TimeValue;
import tools.jackson.databind.JsonNode;

/**
 * The runs of a JMH results file, checked against what the benchmark classes declare, and their
 * summary as CSV.
 *
 * <p>Each benchmark runs in several forks, and now and then a fork's JIT compiles a benchmark into
 * other code than the others, nearly always slower. The published time of a benchmark is therefore
 * the median of its forks' averages; the summary also gives the range and the forks more than 10%
 * from the median.
 */
final class BenchmarkResults {

  /** How far from the median a fork may be before the published results leave it out: 10%. */
  private static final double OUTLIER = 0.10;

  /** Every benchmark class; a full run holds all of them. */
  private static final List<Class<?>> BENCHMARKS =
      List.of(
          CheckBenchmark.class,
          ViolationBenchmark.class,
          BindingBenchmark.class,
          StarterBenchmark.class);

  private BenchmarkResults() {}

  /**
   * One benchmark with one set of parameters, over its forks.
   *
   * @param benchmark the class and method, as in {@code CheckBenchmark.chain}
   * @param params the parameters, in the order JMH lists them
   * @param forks the average time of each fork, in ns
   * @param median the median of all the forks' averages, the published time
   * @param outliers the forks, counted from 1, whose average is more than 10% from the median
   * @param allocated the median of the forks' allocation per call, in bytes
   */
  record Run(
      String benchmark,
      Map<String, String> params,
      List<Double> forks,
      double median,
      List<Integer> outliers,
      double allocated) {

    /** Returns the lowest average of the forks within 10% of the median. */
    double min() {
      return kept().min().orElseThrow();
    }

    /** Returns the highest average of the forks within 10% of the median. */
    double max() {
      return kept().max().orElseThrow();
    }

    private DoubleStream kept() {
      return IntStream.range(0, forks.size())
          .filter(i -> !outliers.contains(i + 1))
          .mapToDouble(forks::get);
    }
  }

  /**
   * Reads every run from JMH's results. The results replace the published ones, so they must come
   * from a full run in the default mode, with as many forks as each benchmark declares.
   */
  static List<Run> read(Path results, JsonNode root) {
    var runs = new ArrayList<Run>();
    for (var node : root) {
      var name = node.get("benchmark").asString();
      var metric = node.get("primaryMetric");
      if (!node.get("mode").asString().equals("avgt")
          || !metric.get("scoreUnit").asString().equals("ns/op")) {
        throw new IllegalArgumentException(
            name + " was not measured as average time in ns/op, as the published results are");
      }
      var forks = averages(metric.get("rawData"));
      var benchmarkClass = benchmarkClass(name.substring(0, name.lastIndexOf('.')));
      var declared = benchmarkClass.getAnnotation(Fork.class).value();
      if (forks.size() != declared) {
        throw new IllegalArgumentException(
            results + " has " + forks.size() + " forks of " + name + ", not " + declared);
      }
      requireDeclared(results, name, node, benchmarkClass);
      var median = median(forks);
      var outliers = new ArrayList<Integer>();
      for (var i = 0; i < forks.size(); i++) {
        if (Math.abs(forks.get(i) / median - 1) > OUTLIER) {
          outliers.add(i + 1);
        }
      }
      var allocation = node.get("secondaryMetrics").get("gc.alloc.rate.norm");
      if (allocation == null) {
        throw new IllegalArgumentException(name + " was measured without -prof gc");
      }
      var params = new LinkedHashMap<String, String>();
      var paramsNode = node.get("params");
      if (paramsNode != null) {
        for (var param : paramsNode.properties()) {
          params.put(param.getKey(), param.getValue().asString());
        }
      }
      runs.add(
          new Run(
              benchmarkClass.getSimpleName() + name.substring(name.lastIndexOf('.')),
              params,
              forks,
              median,
              outliers,
              median(averages(allocation.get("rawData")))));
    }
    requireComplete(results, runs);
    return runs;
  }

  /** Returns the summary that docs/benchmarks links to: one line per benchmark and parameters. */
  static String csv(List<Run> runs) {
    var csv =
        new StringBuilder(
            "Benchmark,Parameters,Median (ns),Min (ns),Max (ns),Forks over 10% from the median: fork (ns),"
                + "Allocated (B)\n");
    for (var run : runs) {
      var params = new ArrayList<String>();
      run.params().forEach((name, value) -> params.add(name + "=" + value));
      csv.append(
          format(
              "%s,%s,%.1f,%.1f,%.1f,%s,%.0f\n",
              run.benchmark(),
              String.join(" ", params),
              run.median(),
              run.min(),
              run.max(),
              run.outliers().stream()
                  .map(fork -> format("%d (%.1f)", fork, run.forks().get(fork - 1)))
                  .collect(Collectors.joining(" ")),
              run.allocated()));
    }
    return csv.toString();
  }

  /** Formats in the root locale, so that the published numbers do not depend on the machine. */
  static String format(String template, Object... args) {
    return String.format(Locale.ROOT, template, args);
  }

  /** Returns the average of each fork's measurements. */
  private static List<Double> averages(JsonNode rawData) {
    var averages = new ArrayList<Double>();
    for (var fork : rawData) {
      var sum = 0.0;
      for (var measurement : fork) {
        sum += measurement.asDouble();
      }
      averages.add(sum / fork.size());
    }
    return averages;
  }

  /** Returns the median, the middle value or the average of the two middle values. */
  private static double median(List<Double> values) {
    var sorted = values.stream().sorted().toList();
    var middle = sorted.size() / 2;
    return sorted.size() % 2 == 1
        ? sorted.get(middle)
        : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
  }

  private static Class<?> benchmarkClass(String className) {
    Class<?> benchmark;
    try {
      benchmark = Class.forName(className);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException(className + " is no benchmark of this module", e);
    }
    if (benchmark.getAnnotation(Fork.class).value() % 2 == 0) {
      throw new IllegalStateException(
          benchmark.getSimpleName()
              + " declares an even number of forks; a median of the forks needs an odd number");
    }
    return benchmark;
  }

  /**
   * Requires that a benchmark ran with the warm-up, measurement and threads that its class
   * declares, as a full run in the default mode does.
   */
  private static void requireDeclared(
      Path results, String name, JsonNode node, Class<?> benchmark) {
    var warmup = benchmark.getAnnotation(Warmup.class);
    var measurement = benchmark.getAnnotation(Measurement.class);
    var threads = benchmark.getAnnotation(Threads.class);
    var declaredThreads =
        threads == null
            ? 1
            : threads.value() == Threads.MAX
                ? Runtime.getRuntime().availableProcessors()
                : threads.value();
    var declared =
        Map.of(
            "warmupIterations", String.valueOf(warmup.iterations()),
            "warmupTime", new TimeValue(warmup.time(), warmup.timeUnit()).toString(),
            "warmupBatchSize", String.valueOf(batchSize(warmup.batchSize())),
            "measurementIterations", String.valueOf(measurement.iterations()),
            "measurementTime", new TimeValue(measurement.time(), measurement.timeUnit()).toString(),
            "measurementBatchSize", String.valueOf(batchSize(measurement.batchSize())),
            "threads", String.valueOf(declaredThreads));
    for (var setting : new TreeMap<>(declared).entrySet()) {
      var actual = node.get(setting.getKey()).asString();
      if (!actual.equals(setting.getValue())) {
        throw new IllegalArgumentException(
            results
                + " has "
                + name
                + " with "
                + setting.getKey()
                + " "
                + actual
                + ", not "
                + setting.getValue());
      }
    }
  }

  /** Returns the batch size that a declared one means: JMH's default, the blank -1, means 1. */
  private static int batchSize(int declared) {
    return Math.max(1, declared);
  }

  /** Requires that the runs hold every method of every benchmark class with every parameter. */
  private static void requireComplete(Path results, List<Run> runs) {
    var measured = new HashMap<String, Set<Map<String, String>>>();
    for (var run : runs) {
      measured.computeIfAbsent(run.benchmark(), key -> new HashSet<>()).add(run.params());
    }
    for (var benchmark : BENCHMARKS) {
      var expected = parameters(benchmark);
      for (var method : benchmark.getMethods()) {
        if (method.getAnnotation(Benchmark.class) == null) {
          continue;
        }
        var name = benchmark.getSimpleName() + "." + method.getName();
        var actual = measured.getOrDefault(name, Set.of());
        if (!actual.equals(expected)) {
          throw new IllegalArgumentException(
              results + " has " + name + " for " + actual + ", not for " + expected);
        }
      }
    }
  }

  /** Returns every combination of the parameters that a benchmark class declares. */
  private static Set<Map<String, String>> parameters(Class<?> benchmark) {
    var combinations = new HashSet<Map<String, String>>();
    combinations.add(Map.of());
    for (var field : benchmark.getFields()) {
      var param = field.getAnnotation(Param.class);
      if (param == null) {
        continue;
      }
      var next = new HashSet<Map<String, String>>();
      for (var prefix : combinations) {
        for (var value : param.value()) {
          var combination = new HashMap<>(prefix);
          combination.put(field.getName(), value);
          next.add(combination);
        }
      }
      combinations = next;
    }
    return combinations;
  }
}
