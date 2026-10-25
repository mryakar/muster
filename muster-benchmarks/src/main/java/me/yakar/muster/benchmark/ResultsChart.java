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
import java.util.regex.Pattern;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes the results of a JMH run: a copy of them, the {@linkplain BenchmarkResults summary} of
 * their medians, and the {@linkplain CheckCostChart chart} that the README shows.
 */
public final class ResultsChart {

  /** The path of the JVM that ran a benchmark, as JMH writes it, up to its file name. */
  private static final Pattern JVM_PATH = Pattern.compile("(\"jvm\"\\s*:\\s*\")[^\"]*\"");

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
    // The path of the JVM names the machine's user and directories; the published copy keeps none.
    var json =
        JVM_PATH.matcher(Files.readString(results).replace("\r\n", "\n")).replaceAll("$1java\"");
    var runs = BenchmarkResults.read(results, JsonMapper.shared().readTree(json));
    // Everything is drawn before anything is written, so a failure leaves the published files as
    // they were.
    var csv = BenchmarkResults.csv(runs);
    var charts = CheckCostChart.draw(runs);
    Files.createDirectories(directory);
    Files.writeString(directory.resolve("results.json"), json);
    Files.writeString(directory.resolve("results.csv"), csv);
    for (var chart : charts.entrySet()) {
      Files.writeString(directory.resolve(chart.getKey()), chart.getValue());
    }
  }
}
