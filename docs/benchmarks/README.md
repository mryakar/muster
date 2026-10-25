# Benchmarks

What Muster adds to a call, measured with [JMH](https://github.com/openjdk/jmh). The benchmarks
live in [`muster-benchmarks`](../../muster-benchmarks), which is not published.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="check-cost-dark.svg">
  <img alt="Time of a passing check by number of rules. With 20 rules, RuleChain.check takes 371 ns and muster.check(target) 382 ns, against 264 ns for calling the same rules by hand." src="check-cost-light.svg">
</picture>

## Budget

Muster checks and orders the rules of a chain once, when the chain is created, or for
`muster.check(target)` on the first check of each input class. It does no reflection while it
checks. A check that passes is held to this budget, against the same rules called by hand:

| | Measured | Budget |
|---|---|---|
| Fixed cost of `RuleChain.check` | about 12 ns | at most 20 ns |
| Cost that `RuleChain.check` adds with 20 rules | about 107 ns | at most 150 ns |
| Fixed cost of `muster.check(target)` | about 22 ns | at most 35 ns |
| Cost that `muster.check(target)` adds with 20 rules | about 117 ns | at most 235 ns |
| Memory a check allocates | 24 bytes per rule, plus up to 40 | at most 24 bytes per rule, plus 48 |
| Fixed cost of the Spring Boot starter, against calling the same binding by hand | about 118 ns and 96 bytes | at most 200 ns and 128 bytes |

Most of what a rule adds is the context it receives: each rule gets its own, 24 bytes, since it
carries the rule's id. For a passing check the memory figures are exact; the JIT does not vary them.

Most of what the starter costs is Spring AOP's own: a proxy whose advice does nothing, measured
the same way, takes about 85 ns, and the 96 bytes are the method invocation that Spring creates for
each call. The starter's advice itself looks up the method's rules in two maps and allocates
nothing.

The numbers hold for the machine described below. A faster machine gives smaller numbers; a change
to Muster is compared with the budget on the same machine.

## Results

The rules do next to nothing, so that the numbers show Muster rather than the rules. Each has a
`check` of its own, as in an application, so the call to it stays virtual. Each cell is the median
of the five forks' average time per check, with the range of the forks within 10% of the median,
and the memory allocated per check.

A check that passes:

| Rules | By hand (baseline) | `RuleChain.check` | `muster.check(target)` |
|---|---|---|---|
| 0 | 1.3 ns (1.3–1.3), 0 B | 13.4 ns (12.8–13.9), 16 B | 22.8 ns (22.7–24.1), 16 B |
| 1 | 11.0 ns (10.9–11.0), 0 B | 11.5 ns (11.4–11.6), 0 B | 23.1 ns (23.1–23.2), 0 B |
| 5 | 65.3 ns (65.1–65.9), 0 B | 101.7 ns (100.7–107.6), 144 B | 113.8 ns (112.4–118.4), 144 B |
| 20 | 264.2 ns (262.9–266.1), 0 B | 371.2 ns (367.5–371.7), 520 B | 381.6 ns (380.3–382.1), 520 B |

With a single rule the JIT inlines the call to it and optimizes the context away, so the check
allocates nothing.

A check of 20 rules that fails, caught as the caller would catch it. The violated rules are spread
evenly, and the last rule is always one of them:

| Violated rules | `FAIL_FAST` | `COLLECT_ALL` |
|---|---|---|
| 1 | 821 ns (813–857), 1,224 B | 875 ns (865–898), 1,224 B |
| 5 | 355 ns (349–357), 600 B | 983 ns (982–1,048), 2,344 B |

`FAIL_FAST` stops at the first violated rule, so with five of them it runs only four rules. The
exception has no stack trace.

`muster.check(target)` from every thread at once, here eight, for a class that is already bound:

| The class is | Time per check |
|---|---|
| the first in its bucket of the chain map | 40.1 ns (39.5–43.5) |
| the second in its bucket | 46.1 ns (42.1–47.0) |

Neither takes a lock. Which classes share a bucket depends on their identity hashes and changes from
run to run, so the benchmark measures both positions. The eight threads share four cores, so a check
takes about twice as long as from a single thread, which checks a target with one rule in 23.1 ns.

A `@Service` method that takes a rule target, checked through the Spring Boot starter's advice,
against a bean that calls the same binding by hand before it does its work:

| Rules | By hand (baseline) | Through the starter |
|---|---|---|
| 0 | 29.3 ns (29.2–29.7), 16 B | 147.4 ns (144.6–153.9), 112 B |
| 1 | 29.1 ns (29.1–29.2), 24 B | 149.3 ns (147.6–154.2), 120 B |
| 20 | 401.3 ns (399.6–411.1), 520 B | 537.1 ns (523.5–547.0), 616 B |

There is no transaction, so that the numbers show Muster.

Now and then a fork's JIT compiles a loop over the rule classes into slower code, by hand as well
as in Muster. In this run, five of the 140 forks ended more than 10% from the median of their
benchmark, and the ranges above leave them out: `RuleChain.check` with 5 rules (117.7 ns),
`muster.check(target)` with 20 rules (420.5 ns), and in a mode given for the check with none
(23.5 ns), the single-rule baseline of the starter benchmark (34.2 ns), and the first bucket of the
multi-threaded benchmark (45.4 ns). The median of five forks keeps such a fork from deciding the
published number.

The raw results are in [`results.json`](results.json), and the medians, ranges and the forks more
than 10% from the median in [`results.csv`](results.csv). `./gradlew jmhReport` writes both and the
chart; the tables and the image descriptions here are updated by hand.

## How it was measured

- Intel Core i7-4700HQ (4 cores, 8 threads, 2013), on mains power, frequency fixed at 2.4 GHz:
  the `performance` governor, turbo off
- Linux 7.0, Amazon Corretto 21.0.11, JMH 1.37, Spring Boot 4.0.8 for the starter
- Five forks, each with five one-second warm-up iterations and five one-second measurements; the
  published time of a benchmark is the median of its forks' averages
- Allocation from JMH's GC profiler (`-prof gc`)
- A fork more than 10% from the median of its benchmark, as when it settles on other compiled code
  than the others, stays out of the published range and is named above

## Running the benchmarks

```bash
./gradlew jmh
```

The run takes about half an hour. For numbers worth comparing, run `jmh` on its own, on a machine
plugged in and otherwise idle, with a fixed CPU frequency. To publish the results here and draw the
chart again:

```bash
./gradlew jmhReport
```
