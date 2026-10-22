# Benchmarks

What Muster adds to a call, measured with [JMH](https://github.com/openjdk/jmh). The benchmarks
live in [`muster-benchmarks`](../../muster-benchmarks), which is not published.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="check-cost-dark.svg">
  <img alt="Time of a passing check by number of rules. With 20 rules, RuleChain.check takes 369 ns and muster.check(target) 383 ns, against 274 ns for calling the same rules by hand." src="check-cost-light.svg">
</picture>

## Budget

Muster checks and orders the rules of a chain once, when the chain is created, or for
`muster.check(target)` on the first check of each input class. It does no reflection while it
checks. A check that passes is held to this budget, against the same rules called by hand:

| | Measured | Budget |
|---|---|---|
| Fixed cost of `RuleChain.check` | about 11 ns | at most 20 ns |
| Cost that `RuleChain.check` adds with 20 rules | about 95 ns | at most 150 ns |
| Fixed cost of `muster.check(target)` | about 20 ns | at most 35 ns |
| Cost that `muster.check(target)` adds with 20 rules | about 109 ns | at most 235 ns |
| Memory a check allocates | 24 bytes per rule, plus up to 40 | at most 24 bytes per rule, plus 48 |

Most of what a rule adds is the context it receives: each rule gets its own, 24 bytes, since it
carries the rule's id. For a passing check the memory figures are exact; the JIT does not vary them.

The numbers hold for the machine described below. A faster machine gives smaller numbers; a change
to Muster is compared with the budget on the same machine.

## Results

The rules do next to nothing, so that the numbers show Muster rather than the rules. Each has a
`check` of its own, as in an application, so the call to it stays virtual. Each cell is the average
time per check with its 99.9% error, and the memory allocated per check.

A check that passes:

| Rules | By hand (baseline) | `RuleChain.check` | `muster.check(target)` |
|---|---|---|---|
| 0 | 1.3 ± 0.0 ns, 0 B | 12.6 ± 0.1 ns, 16 B | 21.5 ± 0.1 ns, 16 B |
| 1 | 11.0 ± 0.1 ns, 0 B | 11.3 ± 0.2 ns, 0 B | 21.9 ± 0.2 ns, 0 B |
| 5 | 65.6 ± 0.9 ns, 0 B | 101.3 ± 2.1 ns, 144 B | 112.2 ± 2.4 ns, 144 B |
| 20 | 273.7 ± 13.6 ns, 0 B | 368.9 ± 5.9 ns, 520 B | 382.6 ± 2.3 ns, 520 B |

With a single rule the JIT inlines the call to it and optimizes the context away, so the check
allocates nothing.

A check of 20 rules that fails, caught as the caller would catch it. The violated rules are spread
evenly, and the last rule is always one of them:

| Violated rules | `FAIL_FAST` | `COLLECT_ALL` |
|---|---|---|
| 1 | 812 ± 2 ns, 1,224 B | 903 ± 30 ns, 1,224 B |
| 5 | 362 ± 11 ns, 600 B | 1,058 ± 39 ns, 2,344 B |

`FAIL_FAST` stops at the first violated rule, so with five of them it runs only four rules. The
exception has no stack trace. The memory of a failing check can differ a little from run to run,
with what the JIT optimizes away.

`muster.check(target)` from every thread at once, here eight, for a class that is already bound:

| The class is | Time per check |
|---|---|
| the first in its bucket of the chain map | 42.8 ± 6.4 ns |
| the second in its bucket | 51.0 ± 12.4 ns |

Neither takes a lock. Which classes share a bucket depends on their identity hashes and changes from
run to run, so the benchmark measures both positions.

The raw results are in [`results.csv`](results.csv). `./gradlew jmhReport` writes it and the chart;
the tables and the image descriptions here are updated by hand.

## How it was measured

- Intel Core i7-4700HQ (4 cores, 8 threads, 2013), on mains power, frequency fixed at 2.4 GHz:
  the `performance` governor, turbo off
- Linux 7.0, Amazon Corretto 21.0.11, JMH 1.37
- Two forks, each with five one-second warm-up iterations and five one-second measurements
- Allocation from JMH's GC profiler (`-prof gc`)
- A fork can settle on other compiled code than the other fork, which shows as a large error. Such a
  run is measured again rather than published.

## Running the benchmarks

```bash
./gradlew jmh
```

The run takes about seven minutes. For numbers worth comparing, run `jmh` on its own, on a machine
plugged in and otherwise idle, with a fixed CPU frequency. To publish the results here and draw the
chart again:

```bash
./gradlew jmhReport
```
