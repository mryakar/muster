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
package me.yakar.muster.benchmark.report;

import static me.yakar.muster.benchmark.report.BenchmarkResults.format;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import me.yakar.muster.benchmark.report.BenchmarkResults.Run;
import org.openjdk.jmh.annotations.Mode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The results as AsciiDoc partials that the benchmarks page of the documentation includes: one
 * table per section of the page, and the forks far from the median. The page explains them by hand.
 *
 * <p>Each cell gives the median of the forks, the range of the forks within 10% of the median in
 * parentheses, and the median allocation: a call in ns and B, with one decimal below 1,000 ns and
 * none from 1,000 on, and a start in ms and MB.
 */
public final class BenchmarkTables {

  /** The first line of each partial. */
  private static final String WRITTEN_BY =
      "// Written from the results of a JMH run by ./gradlew :muster-benchmarks:jmhReport or"
          + " benchmarkTables.\n";

  /**
   * The order of the sections of the page, by benchmark class, for the forks far from the median.
   */
  private static final List<String> PAGE_ORDER =
      List.of(
          "CheckBenchmark",
          "ViolationBenchmark",
          "StarterBenchmark",
          "ListenerBenchmark",
          "CollectionBenchmark",
          "BindingBenchmark",
          "StartupBenchmark");

  /**
   * A column of a table: one benchmark with the parameters that the column fixes.
   *
   * @param header the heading of the column, in AsciiDoc
   * @param benchmark the class and method, as in {@code CheckBenchmark.chain}
   * @param params the parameters that the column fixes, as {@code name=value}
   */
  private record Column(String header, String benchmark, String... params) {}

  /**
   * A row of a table.
   *
   * @param labels the cells that name the row, in AsciiDoc
   * @param params the parameters that the row fixes, as {@code name=value}
   */
  private record Row(List<String> labels, String... params) {

    Row(String label, String... params) {
      this(List.of(label), params);
    }
  }

  /**
   * A table of the page.
   *
   * @param file the name of its partial
   * @param headers the headings of the columns that name the rows
   * @param columns the columns of results
   * @param rows the rows
   */
  private record Table(String file, List<String> headers, List<Column> columns, List<Row> rows) {}

  /** The tables, in the order of the page. */
  private static final List<Table> TABLES =
      List.of(
          new Table(
              "check-passes.adoc",
              List.of("Rules"),
              List.of(
                  new Column("By hand (baseline)", "CheckBenchmark.direct"),
                  new Column("`RuleChain.check`", "CheckBenchmark.chain"),
                  new Column("`muster.check(target)`", "CheckBenchmark.target")),
              List.of(
                  new Row("0", "rules=0"),
                  new Row("1", "rules=1"),
                  new Row("5", "rules=5"),
                  new Row("20", "rules=20"))),
          new Table(
              "check-fails.adoc",
              List.of("Violated rules"),
              List.of(
                  new Column("`FAIL_FAST`", "ViolationBenchmark.check", "mode=FAIL_FAST"),
                  new Column("`COLLECT_ALL`", "ViolationBenchmark.check", "mode=COLLECT_ALL")),
              List.of(new Row("1", "violated=1"), new Row("5", "violated=5"))),
          new Table(
              "starter.adoc",
              List.of("Other advice", "Rules"),
              List.of(
                  new Column("By hand", "StarterBenchmark.manual"),
                  new Column("With the starter", "StarterBenchmark.advised")),
              List.of(
                  new Row(List.of("None", "0"), "advice=none", "rules=0"),
                  new Row(List.of("None", "20"), "advice=none", "rules=20"),
                  new Row(List.of("Method security", "0"), "advice=security", "rules=0"),
                  new Row(List.of("Method security", "20"), "advice=security", "rules=20"),
                  new Row(List.of("Transaction", "0"), "advice=transactional", "rules=0"),
                  new Row(List.of("Transaction", "20"), "advice=transactional", "rules=20"),
                  new Row(List.of("Both", "0"), "advice=both", "rules=0"),
                  new Row(List.of("Both", "20"), "advice=both", "rules=20"))),
          new Table(
              "listener.adoc",
              List.of("Listener"),
              List.of(
                  new Column("No rule", "ListenerBenchmark.target", "rules=0"),
                  new Column("20 rules", "ListenerBenchmark.target", "rules=20")),
              List.of(
                  new Row("None", "listener=none"),
                  new Row("One that does nothing", "listener=noop"),
                  new Row("Observations", "listener=observation"))),
          new Table(
              "collection.adoc",
              List.of("Elements"),
              List.of(
                  new Column("By hand", "CollectionBenchmark.direct"),
                  new Column("`muster.check` on each", "CollectionBenchmark.eachCheck"),
                  new Column("`muster.checkAll`", "CollectionBenchmark.checkAll")),
              List.of(
                  new Row("1", "elements=1"),
                  new Row("5", "elements=5"),
                  new Row("10", "elements=10"),
                  new Row("100", "elements=100"))),
          new Table(
              "threads.adoc",
              List.of("Class"),
              List.of(new Column("`muster.check(target)`", "BindingBenchmark.check")),
              List.of(
                  new Row("Bound first", "bound=first"), new Row("Bound second", "bound=second"))),
          new Table(
              "startup.adoc",
              List.of("Services"),
              List.of(
                  new Column("Plain", "StartupBenchmark.start", "app=plain"),
                  new Column("Proxied", "StartupBenchmark.start", "app=proxied"),
                  new Column("With the starter", "StartupBenchmark.start", "app=muster")),
              List.of(
                  new Row("0", "services=0"),
                  new Row("100", "services=100"),
                  new Row("1,000", "services=1000"))));

  private BenchmarkTables() {}

  /**
   * Writes the partials from the published results, or checks that they are those the published
   * results give, as the build does. The results were checked when they were published, on the
   * machine that ran them.
   *
   * @param args {@code write} or {@code check}, the published results as JSON, and the directory of
   *     the partials
   * @throws IOException if the results cannot be read or the partials cannot be read or written
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 3 || !List.of("write", "check").contains(args[0])) {
      throw new IllegalArgumentException(
          "usage: BenchmarkTables write|check <results.json> <directory>");
    }
    var results = Path.of(args[1]);
    var directory = Path.of(args[2]);
    var partials =
        partials(
            BenchmarkResults.readPublished(
                results, JsonMapper.shared().readTree(Files.readString(results))));
    if (args[0].equals("write")) {
      Files.createDirectories(directory);
      for (var partial : partials.entrySet()) {
        Files.writeString(directory.resolve(partial.getKey()), partial.getValue());
      }
      return;
    }
    // A partial that differs, is missing, or is no longer written.
    var names = new TreeSet<>(partials.keySet());
    try (var files = Files.list(directory)) {
      files.map(file -> file.getFileName().toString()).forEach(names::add);
    }
    var differing = new ArrayList<String>();
    for (var name : names) {
      var file = directory.resolve(name);
      if (!partials.containsKey(name)
          || !Files.exists(file)
          || !Files.readString(file).equals(partials.get(name))) {
        differing.add(name);
      }
    }
    if (!differing.isEmpty()) {
      System.err.println(
          "Not what " + results + " gives, in " + directory + ": " + String.join(", ", differing));
      System.exit(1);
    }
  }

  /** Returns each partial, by the name of its file, in the order of the page. */
  static Map<String, String> partials(List<Run> runs) {
    var byKey = new HashMap<String, Run>();
    for (var run : runs) {
      var key = key(run.benchmark(), run.params().entrySet().stream().map(Map.Entry::toString));
      if (byKey.put(key, run) != null) {
        throw new IllegalArgumentException("The results have " + key + " twice");
      }
    }
    var partials = new LinkedHashMap<String, String>();
    for (var table : TABLES) {
      partials.put(table.file(), table(table, byKey));
    }
    partials.put("forks.adoc", forks(runs));
    partials.put("outliers.adoc", outliers(runs));
    return partials;
  }

  /** Returns a table: a row for each of its rows, and a cell for each of its columns. */
  private static String table(Table table, Map<String, Run> byKey) {
    var cols =
        Stream.concat(
                Collections.nCopies(table.headers().size(), "1").stream(),
                Collections.nCopies(table.columns().size(), "2").stream())
            .collect(Collectors.joining(","));
    var text = new StringBuilder(WRITTEN_BY);
    text.append("[cols=\"").append(cols).append("\"]\n|===\n");
    var headers = new ArrayList<>(table.headers());
    table.columns().forEach(column -> headers.add(column.header()));
    text.append(row(headers)).append('\n');
    for (var row : table.rows()) {
      var cells = new ArrayList<>(row.labels());
      for (var column : table.columns()) {
        var params = Stream.concat(Stream.of(row.params()), Stream.of(column.params()));
        var run = byKey.get(key(column.benchmark(), params));
        if (run == null) {
          throw new IllegalArgumentException(
              "The results have no " + column.benchmark() + " for " + table.file());
        }
        cells.add(cell(run));
      }
      text.append(row(cells));
    }
    return text.append("|===\n").toString();
  }

  /**
   * Returns the numbers of forks that the sentences around the forks far from the median give, as
   * attributes: those that measure an average time and those that measure a start, and how many of
   * each are far from the median.
   */
  private static String forks(List<Run> runs) {
    var text = new StringBuilder(WRITTEN_BY);
    for (var mode : List.of(Mode.AverageTime, Mode.SingleShotTime)) {
      var name = mode == Mode.AverageTime ? "benchmark-forks" : "benchmark-starts";
      var ofMode = runs.stream().filter(run -> run.mode() == mode).toList();
      text.append(
          format(
              ":%s: %d\n:%s-far: %d\n",
              name,
              ofMode.stream().mapToInt(run -> run.forks().size()).sum(),
              name,
              ofMode.stream().mapToInt(run -> run.outliers().size()).sum()));
    }
    return text.toString();
  }

  /**
   * Returns a list of the forks far from the median, each with its benchmark, parameters and
   * average, in the order of the page.
   */
  private static String outliers(List<Run> runs) {
    var text = new StringBuilder(WRITTEN_BY);
    var sorted =
        runs.stream()
            .sorted(
                Comparator.comparingInt(
                    run ->
                        PAGE_ORDER.indexOf(
                            run.benchmark().substring(0, run.benchmark().indexOf('.')))))
            .toList();
    for (var run : sorted) {
      for (var fork : run.outliers()) {
        var params =
            run.params().entrySet().stream()
                .map(param -> "`" + param + "`")
                .collect(Collectors.joining(" "));
        text.append(
            format(
                "* `%s` %s: %s\n", run.benchmark(), params, time(run, run.forks().get(fork - 1))));
      }
    }
    return text.toString();
  }

  /**
   * Returns the cell of a run: the median, the range of the forks within 10% of the median, and the
   * allocation, as in {@code 390.9 ns (381.1–415.3), 520 B}. The range takes the decimals of the
   * median.
   */
  private static String cell(Run run) {
    var singleShot = run.mode() == Mode.SingleShotTime;
    var decimals = singleShot ? 0 : decimals(run.median());
    return format(
        "%s %s (%s–%s), %s",
        number(run.median(), decimals),
        singleShot ? "ms" : "ns",
        number(run.min(), decimals),
        number(run.max(), decimals),
        singleShot
            ? format("%,.1f MB", run.allocated() / 1_000_000)
            : format("%,.0f B", run.allocated()));
  }

  /** Returns a time of a run with its unit: a start in ms, a call in ns. */
  private static String time(Run run, double value) {
    return run.mode() == Mode.SingleShotTime
        ? number(value, 0) + " ms"
        : number(value, decimals(value)) + " ns";
  }

  /** Returns the decimals of a time in ns: one below 1,000 ns, and none from 1,000 ns on. */
  private static int decimals(double nanos) {
    return Math.round(nanos * 10) / 10.0 < 1_000 ? 1 : 0;
  }

  /** Returns a number with the decimals given, and its thousands separated by commas. */
  private static String number(double value, int decimals) {
    return format("%,." + decimals + "f", value);
  }

  /** Returns a row of a table, one line. */
  private static String row(List<String> cells) {
    return cells.stream().map(cell -> "|" + cell).collect(Collectors.joining(" ")) + "\n";
  }

  /** Returns the key of one benchmark with one set of parameters, in any order. */
  private static String key(String benchmark, Stream<String> params) {
    return benchmark + " " + params.sorted().collect(Collectors.joining(" "));
  }
}
