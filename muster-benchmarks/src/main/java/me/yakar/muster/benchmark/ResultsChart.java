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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Param;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Draws the results of {@link CheckBenchmark} as a line chart for the README: the time of a passing
 * check against the number of rules, with the rules called by hand as the baseline. It writes the
 * chart as SVG in a light and a dark variant, next to a copy of the results and a summary of them.
 *
 * <p>Each benchmark runs in several forks, and now and then a fork's JIT compiles the loop over
 * twenty rule classes into slower code. The published time of a benchmark is therefore the median
 * of its forks' averages; the summary also gives the range and the forks more than 10% from the
 * median.
 */
public final class ResultsChart {

  private static final int WIDTH = 720;
  private static final int HEIGHT = 380;
  private static final int LEFT = 64;
  private static final int RIGHT = 236;
  private static final int TOP = 20;
  private static final int BOTTOM = 56;
  private static final int MIN_LABEL_GAP = 18;
  private static final String FONT =
      "-apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif";

  /** The colors of GitHub's light and dark themes, where the README is read. */
  private enum Theme {
    LIGHT("light", "#1f2328", "#59636e", "#d1d9e0", "#ffffff", "#2a78d6", "#169a6b", "#818b98"),
    DARK("dark", "#f0f6fc", "#9198a1", "#3d444d", "#0d1117", "#3987e5", "#199e70", "#9198a1");

    final String name;
    final String ink;
    final String muted;
    final String grid;
    final String surface;
    final String chain;
    final String target;
    final String direct;

    Theme(
        String name,
        String ink,
        String muted,
        String grid,
        String surface,
        String chain,
        String target,
        String direct) {
      this.name = name;
      this.ink = ink;
      this.muted = muted;
      this.grid = grid;
      this.surface = surface;
      this.chain = chain;
      this.target = target;
      this.direct = direct;
    }
  }

  /** A line of the chart. */
  private record Series(String benchmark, String label, String dash) {

    String color(Theme theme) {
      return switch (benchmark) {
        case "chain" -> theme.chain;
        case "target" -> theme.target;
        default -> theme.direct;
      };
    }
  }

  private static final List<Series> SERIES =
      List.of(
          new Series("target", "muster.check(target)", "7 4"),
          new Series("chain", "RuleChain.check", ""),
          new Series("direct", "By hand (baseline)", "2 4"));

  private ResultsChart() {}

  /**
   * Draws the chart and writes the summary.
   *
   * @param args the JMH results as JSON, and the directory to write the chart and the results to
   * @throws IOException if the results cannot be read or the chart cannot be written
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 2) {
      throw new IllegalArgumentException("usage: ResultsChart <results.json> <directory>");
    }
    var results = Path.of(args[0]);
    var directory = Path.of(args[1]);
    // JMH ends lines as the platform does; the copy ends them the same everywhere.
    var json = Files.readString(results).replace("\r\n", "\n");
    var runs = summarize(results, JsonMapper.shared().readTree(json));
    var scores = scores(results, runs);
    // Everything is drawn before anything is written, so a failure leaves the published files.
    var charts = new LinkedHashMap<String, String>();
    for (var theme : Theme.values()) {
      charts.put("check-cost-" + theme.name + ".svg", draw(scores, theme));
    }
    Files.createDirectories(directory);
    Files.writeString(directory.resolve("results.json"), json);
    Files.writeString(directory.resolve("results.csv"), csv(runs));
    for (var chart : charts.entrySet()) {
      Files.writeString(directory.resolve(chart.getKey()), chart.getValue());
    }
  }

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
  private record Run(
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
  private static List<Run> summarize(Path results, JsonNode root) {
    var runs = new ArrayList<Run>();
    for (var node : root) {
      var name = node.get("benchmark").asString();
      var className = name.substring(0, name.lastIndexOf('.'));
      var benchmark = name.substring(name.lastIndexOf('.', name.lastIndexOf('.') - 1) + 1);
      var metric = node.get("primaryMetric");
      if (!node.get("mode").asString().equals("avgt")
          || !metric.get("scoreUnit").asString().equals("ns/op")) {
        throw new IllegalArgumentException(
            name + " was not measured as average time in ns/op, as the published results are");
      }
      var forks = averages(metric.get("rawData"));
      var declared = declaredForks(className);
      if (forks.size() != declared) {
        throw new IllegalArgumentException(
            results + " has " + forks.size() + " forks of " + name + ", not " + declared);
      }
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
              benchmark,
              params,
              forks,
              median,
              outliers,
              median(averages(allocation.get("rawData")))));
    }
    return runs;
  }

  /** How far from the median a fork may be before the published results leave it out: 10%. */
  private static final double OUTLIER = 0.10;

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

  private static int declaredForks(String className) {
    try {
      return Class.forName(className).getAnnotation(Fork.class).value();
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException(className + " is no benchmark of this module", e);
    }
  }

  /** Writes the summary that docs/benchmarks links to: one line per benchmark and parameters. */
  private static String csv(List<Run> runs) {
    var csv =
        new StringBuilder(
            "Benchmark,Parameters,Median (ns),Min (ns),Max (ns),Forks over 10% from the median (ns),"
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

  /**
   * Returns the median times of {@link CheckBenchmark}, by benchmark, then by the number of rules,
   * and checks that the results hold every benchmark with every parameter.
   */
  private static Map<String, TreeMap<Integer, Double>> scores(Path results, List<Run> runs) {
    var scores = new LinkedHashMap<String, TreeMap<Integer, Double>>();
    var measured = new HashMap<String, Set<Map<String, String>>>();
    for (var run : runs) {
      var className = run.benchmark().substring(0, run.benchmark().indexOf('.'));
      measured.computeIfAbsent(className, key -> new HashSet<>()).add(run.params());
      if (className.equals(CheckBenchmark.class.getSimpleName())) {
        scores
            .computeIfAbsent(
                run.benchmark().substring(className.length() + 1), key -> new TreeMap<>())
            .put(Integer.parseInt(run.params().get("rules")), run.median());
      }
    }
    var ruleCounts = new TreeSet<Integer>();
    for (var count : params(CheckBenchmark.class, "rules")) {
      ruleCounts.add(Integer.parseInt(count));
    }
    for (var series : SERIES) {
      var counts = scores.getOrDefault(series.benchmark(), new TreeMap<>()).keySet();
      if (!counts.equals(ruleCounts)) {
        throw new IllegalArgumentException(
            results
                + " has CheckBenchmark."
                + series.benchmark()
                + " for "
                + counts
                + " rules, not for "
                + ruleCounts);
      }
    }
    expect(results, measured, ViolationBenchmark.class, "violated", "mode");
    expect(results, measured, BindingBenchmark.class, "bound");
    expect(results, measured, StarterBenchmark.class, "rules");
    return scores;
  }

  /** Checks that the results hold a benchmark class with every combination of its parameters. */
  private static void expect(
      Path results,
      Map<String, Set<Map<String, String>>> measured,
      Class<?> benchmark,
      String... fields) {
    var expected = new HashSet<Map<String, String>>();
    expected.add(Map.of());
    for (var field : fields) {
      var next = new HashSet<Map<String, String>>();
      for (var prefix : expected) {
        for (var value : params(benchmark, field)) {
          var combination = new HashMap<>(prefix);
          combination.put(field, value);
          next.add(combination);
        }
      }
      expected = next;
    }
    var actual = measured.getOrDefault(benchmark.getSimpleName(), Set.of());
    if (!actual.equals(expected)) {
      throw new IllegalArgumentException(
          results
              + " has "
              + benchmark.getSimpleName()
              + " for "
              + actual
              + ", not for "
              + expected);
    }
  }

  /** Returns the values a benchmark declares for one of its parameters. */
  private static List<String> params(Class<?> benchmark, String field) {
    try {
      return List.of(benchmark.getField(field).getAnnotation(Param.class).value());
    } catch (NoSuchFieldException e) {
      throw new IllegalStateException(benchmark.getSimpleName() + " has no parameter " + field, e);
    }
  }

  private static String draw(Map<String, TreeMap<Integer, Double>> scores, Theme theme) {
    var maxRules = scores.get("chain").lastKey();
    var maxScore =
        scores.values().stream()
            .flatMap(series -> series.values().stream())
            .max(Comparator.naturalOrder())
            .orElseThrow();
    var step = step(maxScore);
    var top = Math.ceil(maxScore / step) * step;
    var plotWidth = WIDTH - LEFT - RIGHT;
    var plotHeight = HEIGHT - TOP - BOTTOM;
    var x = (Scale) value -> LEFT + value / maxRules * plotWidth;
    var y = (Scale) value -> TOP + plotHeight - value / top * plotHeight;

    var svg = new StringBuilder();
    svg.append(
            format(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" viewBox=\"0 0"
                    + " %d %d\" role=\"img\" aria-labelledby=\"title desc\" font-family=\"%s\">\n",
                WIDTH, HEIGHT, WIDTH, HEIGHT, FONT))
        .append("  <title id=\"title\">Time of a passing check by number of rules</title>\n")
        .append("  <desc id=\"desc\">")
        .append(description(scores))
        .append("</desc>\n");

    for (var tick = 0.0; tick <= top; tick += step) {
      var tickY = y.at(tick);
      svg.append(
          format(
              "  <line x1=\"%d\" y1=\"%.1f\" x2=\"%d\" y2=\"%.1f\" stroke=\"%s\"/>\n",
              LEFT, tickY, LEFT + plotWidth, tickY, theme.grid));
      svg.append(
          format(
              "  <text x=\"%d\" y=\"%.1f\" fill=\"%s\" font-size=\"12\" text-anchor=\"end\""
                  + " dominant-baseline=\"middle\">%.0f</text>\n",
              LEFT - 8, tickY, theme.muted, tick));
    }
    for (var rules : scores.get("chain").keySet()) {
      svg.append(
          format(
              "  <text x=\"%.1f\" y=\"%d\" fill=\"%s\" font-size=\"12\""
                  + " text-anchor=\"middle\">%d</text>\n",
              x.at(rules), TOP + plotHeight + 20, theme.muted, rules));
    }
    svg.append(
        format(
            "  <text x=\"%.1f\" y=\"%d\" fill=\"%s\" font-size=\"12\""
                + " text-anchor=\"middle\">Rules in the check</text>\n",
            LEFT + plotWidth / 2.0, HEIGHT - 12, theme.muted));
    svg.append(
        format(
            "  <text x=\"16\" y=\"%.1f\" fill=\"%s\" font-size=\"12\" text-anchor=\"middle\""
                + " transform=\"rotate(-90 16 %.1f)\">ns per check</text>\n",
            TOP + plotHeight / 2.0, theme.muted, TOP + plotHeight / 2.0));

    for (var series : SERIES) {
      var points = new StringBuilder();
      scores
          .get(series.benchmark())
          .forEach((rules, ns) -> points.append(format("%.1f,%.1f ", x.at(rules), y.at(ns))));
      svg.append(
          format(
              "  <polyline points=\"%s\" fill=\"none\" stroke=\"%s\" stroke-width=\"2\""
                  + " stroke-linejoin=\"round\" stroke-linecap=\"round\"%s/>\n",
              points.toString().strip(),
              series.color(theme),
              series.dash().isEmpty() ? "" : " stroke-dasharray=\"" + series.dash() + "\""));
      scores
          .get(series.benchmark())
          .forEach(
              (rules, ns) ->
                  svg.append(
                      format(
                          "  <circle cx=\"%.1f\" cy=\"%.1f\" r=\"4\" fill=\"%s\" stroke=\"%s\""
                              + " stroke-width=\"2\"/>\n",
                          x.at(rules), y.at(ns), series.color(theme), theme.surface)));
    }

    // Each line is named at its end, with its last value; labels that would overlap move apart.
    var labels = new ArrayList<double[]>();
    for (var series : SERIES) {
      labels.add(new double[] {y.at(scores.get(series.benchmark()).lastEntry().getValue())});
    }
    spread(labels);
    for (var i = 0; i < SERIES.size(); i++) {
      var series = SERIES.get(i);
      var last = scores.get(series.benchmark()).lastEntry().getValue();
      svg.append(
          format(
              "  <text x=\"%d\" y=\"%.1f\" fill=\"%s\" font-size=\"13\""
                  + " dominant-baseline=\"middle\"><tspan fill=\"%s\">●</tspan> %s"
                  + " <tspan fill=\"%s\">%.0f ns</tspan></text>\n",
              LEFT + plotWidth + 14,
              labels.get(i)[0],
              theme.ink,
              series.color(theme),
              series.label(),
              theme.muted,
              last));
    }
    return svg.append("</svg>\n").toString();
  }

  /** Moves label positions apart, top to bottom, until no two are closer than the minimum gap. */
  private static void spread(List<double[]> labels) {
    var sorted = new ArrayList<>(labels);
    sorted.sort(Comparator.comparingDouble(label -> label[0]));
    for (var i = 1; i < sorted.size(); i++) {
      var above = sorted.get(i - 1)[0];
      if (sorted.get(i)[0] - above < MIN_LABEL_GAP) {
        sorted.get(i)[0] = above + MIN_LABEL_GAP;
      }
    }
  }

  /** The step between gridlines: a round number that gives at most six of them. */
  private static double step(double max) {
    if (max <= 0) {
      throw new IllegalArgumentException("the results have no positive score");
    }
    for (var step : new double[] {10, 20, 25, 50, 100, 200, 250, 500, 1000}) {
      if (max / step <= 6) {
        return step;
      }
    }
    return Math.pow(10, Math.ceil(Math.log10(max / 6)));
  }

  private static String description(Map<String, TreeMap<Integer, Double>> scores) {
    var text = new StringBuilder();
    for (var series : SERIES) {
      text.append(series.label()).append(':');
      scores
          .get(series.benchmark())
          .forEach(
              (rules, ns) ->
                  text.append(format(" %d %s %.1f ns,", rules, rules == 1 ? "rule" : "rules", ns)));
      text.setLength(text.length() - 1);
      text.append(". ");
    }
    return text.toString().strip();
  }

  private static String format(String template, Object... args) {
    return String.format(Locale.ROOT, template, args);
  }

  /** Maps a value to a coordinate of the chart. */
  @FunctionalInterface
  private interface Scale {
    double at(double value);
  }
}
