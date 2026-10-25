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

import static me.yakar.muster.benchmark.BenchmarkResults.format;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import me.yakar.muster.benchmark.BenchmarkResults.Run;

/**
 * The line chart of {@link CheckBenchmark} that the README shows: the time of a passing check
 * against the number of rules, with the rules called by hand as the baseline. It is drawn as SVG in
 * a light and a dark variant.
 */
final class CheckCostChart {

  private static final int WIDTH = 720;
  private static final int HEIGHT = 380;
  private static final int LEFT = 64;
  private static final int RIGHT = 236;
  private static final int TOP = 20;
  private static final int BOTTOM = 56;
  private static final int MIN_LABEL_GAP = 18;
  private static final String FONT =
      "-apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif";

  /** The colors of one of GitHub's themes, where the README is read. */
  private record Theme(
      String name,
      String ink,
      String muted,
      String grid,
      String surface,
      String chain,
      String target,
      String direct) {}

  private static final List<Theme> THEMES =
      List.of(
          new Theme(
              "light", "#1f2328", "#59636e", "#d1d9e0", "#ffffff", "#2a78d6", "#169a6b", "#818b98"),
          new Theme(
              "dark", "#f0f6fc", "#9198a1", "#3d444d", "#0d1117", "#3987e5", "#199e70", "#9198a1"));

  /** A line of the chart, with the color that each theme gives it. */
  private record Series(
      String benchmark, String label, String dash, Function<Theme, String> color) {}

  private static final List<Series> SERIES =
      List.of(
          new Series("target", "muster.check(target)", "7 4", Theme::target),
          new Series("chain", "RuleChain.check", "", Theme::chain),
          new Series("direct", "By hand (baseline)", "2 4", Theme::direct));

  /** Maps a value to a coordinate of the chart. */
  @FunctionalInterface
  private interface Scale {
    double at(double value);
  }

  private CheckCostChart() {}

  /** Returns the chart in each theme, by the name of its file. */
  static Map<String, String> draw(List<Run> runs) {
    var scores = scores(runs);
    var charts = new LinkedHashMap<String, String>();
    for (var theme : THEMES) {
      charts.put("check-cost-" + theme.name() + ".svg", draw(scores, theme));
    }
    return charts;
  }

  /** Returns the median times of {@link CheckBenchmark}, by benchmark, then by number of rules. */
  private static Map<String, TreeMap<Integer, Double>> scores(List<Run> runs) {
    var scores = new LinkedHashMap<String, TreeMap<Integer, Double>>();
    var check = CheckBenchmark.class.getSimpleName() + ".";
    for (var run : runs) {
      if (run.benchmark().startsWith(check)) {
        scores
            .computeIfAbsent(run.benchmark().substring(check.length()), key -> new TreeMap<>())
            .put(Integer.parseInt(run.params().get("rules")), run.median());
      }
    }
    return scores;
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
              LEFT, tickY, LEFT + plotWidth, tickY, theme.grid()));
      svg.append(
          format(
              "  <text x=\"%d\" y=\"%.1f\" fill=\"%s\" font-size=\"12\" text-anchor=\"end\""
                  + " dominant-baseline=\"middle\">%.0f</text>\n",
              LEFT - 8, tickY, theme.muted(), tick));
    }
    for (var rules : scores.get("chain").keySet()) {
      svg.append(
          format(
              "  <text x=\"%.1f\" y=\"%d\" fill=\"%s\" font-size=\"12\""
                  + " text-anchor=\"middle\">%d</text>\n",
              x.at(rules), TOP + plotHeight + 20, theme.muted(), rules));
    }
    svg.append(
        format(
            "  <text x=\"%.1f\" y=\"%d\" fill=\"%s\" font-size=\"12\""
                + " text-anchor=\"middle\">Rules in the check</text>\n",
            LEFT + plotWidth / 2.0, HEIGHT - 12, theme.muted()));
    svg.append(
        format(
            "  <text x=\"16\" y=\"%.1f\" fill=\"%s\" font-size=\"12\" text-anchor=\"middle\""
                + " transform=\"rotate(-90 16 %.1f)\">ns per check</text>\n",
            TOP + plotHeight / 2.0, theme.muted(), TOP + plotHeight / 2.0));

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
              series.color().apply(theme),
              series.dash().isEmpty() ? "" : " stroke-dasharray=\"" + series.dash() + "\""));
      scores
          .get(series.benchmark())
          .forEach(
              (rules, ns) ->
                  svg.append(
                      format(
                          "  <circle cx=\"%.1f\" cy=\"%.1f\" r=\"4\" fill=\"%s\" stroke=\"%s\""
                              + " stroke-width=\"2\"/>\n",
                          x.at(rules), y.at(ns), series.color().apply(theme), theme.surface())));
    }

    // Each line is named at its end, with its last value; labels that would overlap move apart.
    var last =
        SERIES.stream()
            .mapToDouble(series -> scores.get(series.benchmark()).lastEntry().getValue())
            .toArray();
    var labelYs = spread(Arrays.stream(last).map(y::at).toArray());
    for (var i = 0; i < SERIES.size(); i++) {
      var series = SERIES.get(i);
      svg.append(
          format(
              "  <text x=\"%d\" y=\"%.1f\" fill=\"%s\" font-size=\"13\""
                  + " dominant-baseline=\"middle\"><tspan fill=\"%s\">●</tspan> %s"
                  + " <tspan fill=\"%s\">%.0f ns</tspan></text>\n",
              LEFT + plotWidth + 14,
              labelYs[i],
              theme.ink(),
              series.color().apply(theme),
              series.label(),
              theme.muted(),
              last[i]));
    }
    return svg.append("</svg>\n").toString();
  }

  /** Returns the label positions moved apart, top to bottom, until none is too close to another. */
  private static double[] spread(double[] ys) {
    var spread = ys.clone();
    var order =
        IntStream.range(0, ys.length)
            .boxed()
            .sorted(Comparator.comparingDouble(i -> ys[i]))
            .mapToInt(Integer::intValue)
            .toArray();
    for (var i = 1; i < order.length; i++) {
      var above = spread[order[i - 1]];
      if (spread[order[i]] - above < MIN_LABEL_GAP) {
        spread[order[i]] = above + MIN_LABEL_GAP;
      }
    }
    return spread;
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
    return SERIES.stream()
        .map(
            series ->
                scores.get(series.benchmark()).entrySet().stream()
                    .map(
                        score -> {
                          var rules = score.getKey();
                          return format(
                              " %d %s %.1f ns",
                              rules, rules == 1 ? "rule" : "rules", score.getValue());
                        })
                    .collect(Collectors.joining(",", series.label() + ":", ".")))
        .collect(Collectors.joining(" "));
  }
}
