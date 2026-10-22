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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.openjdk.jmh.annotations.Param;

/**
 * Draws the results of {@link CheckBenchmark} as a line chart for the README: the time of a passing
 * check against the number of rules, with the rules called by hand as the baseline. It writes the
 * chart as SVG in a light and a dark variant, next to a copy of the results.
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
   * Draws the chart.
   *
   * @param args the JMH results as CSV, and the directory to write the chart to
   * @throws IOException if the results cannot be read or the chart cannot be written
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 2) {
      throw new IllegalArgumentException("usage: ResultsChart <results.csv> <directory>");
    }
    var results = Path.of(args[0]);
    var directory = Path.of(args[1]);
    var lines = Files.readAllLines(results);
    var scores = read(results, lines);
    // Everything is drawn before anything is written, so a failure leaves the published files.
    var charts = new LinkedHashMap<String, String>();
    for (var theme : Theme.values()) {
      charts.put("check-cost-" + theme.name + ".svg", draw(scores, theme));
    }
    Files.createDirectories(directory);
    // JMH ends lines as the platform does; the copy ends them the same everywhere.
    Files.writeString(directory.resolve("results.csv"), String.join("\n", lines) + "\n");
    for (var chart : charts.entrySet()) {
      Files.writeString(directory.resolve(chart.getKey()), chart.getValue());
    }
  }

  /**
   * Reads the scores of {@link CheckBenchmark}: by benchmark, then by the number of rules. The
   * results replace the published ones, so they must come from a full run in the default mode.
   */
  private static Map<String, TreeMap<Integer, Double>> read(Path results, List<String> lines) {
    if (lines.isEmpty()) {
      throw new IllegalArgumentException(results + " is empty");
    }
    var header = split(lines.getFirst());
    var benchmark = header.indexOf("Benchmark");
    var mode = header.indexOf("Mode");
    var score = header.indexOf("Score");
    var unit = header.indexOf("Unit");
    var rules = header.indexOf("Param: rules");
    var violated = header.indexOf("Param: violated");
    var checkMode = header.indexOf("Param: mode");
    var prefix = CheckBenchmark.class.getName() + ".";
    var violations = new HashSet<List<String>>();
    var scores = new LinkedHashMap<String, TreeMap<Integer, Double>>();
    for (var line : lines.subList(1, lines.size())) {
      var cells = split(line);
      var name = cells.get(benchmark);
      if (name.contains(":")) {
        continue;
      }
      if (!cells.get(mode).equals("avgt") || !cells.get(unit).equals("ns/op")) {
        throw new IllegalArgumentException(
            name
                + " was measured as "
                + cells.get(mode)
                + " in "
                + cells.get(unit)
                + "; the published results are average times in ns/op");
      }
      if (name.startsWith(prefix)) {
        scores
            .computeIfAbsent(name.substring(prefix.length()), key -> new TreeMap<>())
            .put(Integer.parseInt(cells.get(rules)), Double.parseDouble(cells.get(score)));
      }
      if (name.startsWith(ViolationBenchmark.class.getName() + ".")) {
        violations.add(List.of(cells.get(violated), cells.get(checkMode)));
      }
    }
    var ruleCounts = new TreeSet<Integer>();
    for (var count : params(CheckBenchmark.class, "rules")) {
      ruleCounts.add(Integer.parseInt(count));
    }
    for (var series : SERIES) {
      var measured = scores.getOrDefault(series.benchmark(), new TreeMap<>()).keySet();
      if (!measured.equals(ruleCounts)) {
        throw new IllegalArgumentException(
            results
                + " has CheckBenchmark."
                + series.benchmark()
                + " for "
                + measured
                + " rules, not for "
                + ruleCounts);
      }
    }
    var expected = new HashSet<List<String>>();
    for (var count : params(ViolationBenchmark.class, "violated")) {
      for (var checked : params(ViolationBenchmark.class, "mode")) {
        expected.add(List.of(count, checked));
      }
    }
    if (!violations.equals(expected)) {
      throw new IllegalArgumentException(
          results + " has ViolationBenchmark for " + violations + ", not for " + expected);
    }
    return scores;
  }

  /** Returns the values a benchmark declares for one of its parameters. */
  private static List<String> params(Class<?> benchmark, String field) {
    try {
      return List.of(benchmark.getField(field).getAnnotation(Param.class).value());
    } catch (NoSuchFieldException e) {
      throw new IllegalStateException(benchmark.getSimpleName() + " has no parameter " + field, e);
    }
  }

  /** Splits a line of JMH's CSV, whose text cells are quoted and hold no quotes or commas. */
  private static List<String> split(String line) {
    var cells = new ArrayList<String>();
    for (var cell : line.split(",", -1)) {
      cells.add(cell.startsWith("\"") ? cell.substring(1, cell.length() - 1) : cell);
    }
    return cells;
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
