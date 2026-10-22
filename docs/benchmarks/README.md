# Benchmarks

What Muster adds to a call, measured with [JMH](https://github.com/openjdk/jmh). The benchmarks
live in [`muster-benchmarks`](../../muster-benchmarks), which is not published.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="check-cost-dark.svg">
  <img alt="Time of a passing check by number of rules. With 20 rules, RuleChain.check takes 114 ns and muster.check(target) 128 ns, against 72 ns for calling the same rules by hand." src="check-cost-light.svg">
</picture>

## Budget

Muster resolves its rules once, when it starts, and does no reflection while it checks. A check
that passes is held to this budget, against the same rules called by hand:

| | Measured | Budget |
|---|---|---|
| Fixed cost of a check | about 12 ns | at most 20 ns |
| Cost that a check of 20 rules adds | about 43 ns | at most 75 ns |
| Memory a check allocates, up to 20 rules | 16 to 40 bytes | at most 64 bytes |

The numbers hold for the machine described below. A faster machine gives smaller numbers; a change
to Muster is compared with the budget on the same machine.

## Results

The rules do next to nothing, so that the numbers show Muster rather than the rules. Each cell is
the average time per check with its 99.9% error, and the memory allocated per check.

A check that passes:

| Rules | By hand (baseline) | `RuleChain.check` | `muster.check(target)` |
|---|---|---|---|
| 0 | 1.5 ± 0.2 ns, 0 B | 13.1 ± 0.8 ns, 16 B | 24.2 ± 0.4 ns, 16 B |
| 1 | 11.1 ± 0.2 ns, 0 B | 11.5 ± 0.2 ns, 0 B | 27.5 ± 1.2 ns, 0 B |
| 5 | 24.4 ± 0.3 ns, 0 B | 36.6 ± 0.7 ns, 24 B | 59.6 ± 7.7 ns, 24 B |
| 20 | 72.0 ± 3.1 ns, 0 B | 114.5 ± 26.6 ns, 40 B | 127.9 ± 5.8 ns, 40 B |

A check of 20 rules that fails, caught as the caller would catch it. The violated rules are spread
evenly, and the last rule is always one of them:

| Violated rules | `FAIL_FAST` | `COLLECT_ALL` |
|---|---|---|
| 1 | 392 ± 23 ns, 504 B | 489 ± 2 ns, 504 B |
| 5 | 299 ± 10 ns, 504 B | 920 ± 31 ns, 2,344 B |

`FAIL_FAST` stops at the first violated rule, so with five of them it runs only four rules. The
exception has no stack trace.

The raw results are in [`results.csv`](results.csv).

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

The run takes about six minutes. For numbers worth comparing, run on a machine plugged in and
otherwise idle, with a fixed CPU frequency. To publish the results here and draw the chart again:

```bash
./gradlew jmhReport
```
