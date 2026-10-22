# Benchmarks

What Muster adds to a call, measured with [JMH](https://github.com/openjdk/jmh). The benchmarks
live in [`muster-benchmarks`](../../muster-benchmarks), which is not published.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="check-cost-dark.svg">
  <img alt="Time of a passing check by number of rules. With 20 rules, RuleChain.check takes 111 ns and muster.check(target) 127 ns, against 69 ns for calling the same rules by hand." src="check-cost-light.svg">
</picture>

## Budget

Muster checks and orders the rules of a chain once, when the chain is created, or for
`muster.check(target)` on the first check of each input class. It does no reflection while it
checks. A check that passes is held to this budget, against the same rules called by hand:

| | Measured | Budget |
|---|---|---|
| Fixed cost of `RuleChain.check` | about 12 ns | at most 20 ns |
| Cost that `RuleChain.check` adds with 20 rules | about 42 ns | at most 75 ns |
| Fixed cost of `muster.check(target)` | about 22 ns | at most 35 ns |
| Cost that `muster.check(target)` adds with 20 rules | about 58 ns | at most 85 ns |
| Memory a check allocates, up to 20 rules | 0 to 40 bytes | at most 64 bytes |

The numbers hold for the machine described below. A faster machine gives smaller numbers; a change
to Muster is compared with the budget on the same machine.

## Results

The rules do next to nothing, so that the numbers show Muster rather than the rules. Each cell is
the average time per check with its 99.9% error, and the memory allocated per check.

A check that passes:

| Rules | By hand (baseline) | `RuleChain.check` | `muster.check(target)` |
|---|---|---|---|
| 0 | 1.3 ± 0.0 ns, 0 B | 13.3 ± 1.1 ns, 16 B | 23.7 ± 0.2 ns, 16 B |
| 1 | 11.1 ± 0.1 ns, 0 B | 11.2 ± 0.1 ns, 0 B | 27.5 ± 0.4 ns, 0 B |
| 5 | 24.1 ± 0.2 ns, 0 B | 39.0 ± 0.2 ns, 24 B | 56.0 ± 2.7 ns, 24 B |
| 20 | 68.7 ± 0.5 ns, 0 B | 110.9 ± 1.4 ns, 40 B | 127.2 ± 0.9 ns, 40 B |

A check of 20 rules that fails, caught as the caller would catch it. The violated rules are spread
evenly, and the last rule is always one of them:

| Violated rules | `FAIL_FAST` | `COLLECT_ALL` |
|---|---|---|
| 1 | 382 ± 2 ns, 504 B | 505 ± 31 ns, 504 B |
| 5 | 287 ± 6 ns, 504 B | 861 ± 87 ns, 2,104 B |

`FAIL_FAST` stops at the first violated rule, so with five of them it runs only four rules. The
exception has no stack trace.

The raw results are in [`results.csv`](results.csv). `./gradlew jmhReport` writes it and the chart;
the tables and the image descriptions here are updated by hand.

## How it was measured

- Intel Core i7-4700HQ (4 cores, 8 threads, 2013), on mains power, frequency fixed at 2.4 GHz:
  the `performance` governor, turbo off
- Linux 7.0, Amazon Corretto 21.0.11, JMH 1.37
- Two forks, each with five one-second warm-up iterations and five one-second measurements
- Allocation from JMH's GC profiler (`-prof gc`)

## Running the benchmarks

```bash
./gradlew jmh
```

The run takes about six minutes. For numbers worth comparing, run `jmh` on its own, on a machine
plugged in and otherwise idle, with a fixed CPU frequency. To publish the results here and draw the
chart again:

```bash
./gradlew jmhReport
```
