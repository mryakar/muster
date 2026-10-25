# Benchmarks

What Muster adds to a call, measured with [JMH](https://github.com/openjdk/jmh). The benchmarks
live in [`muster-benchmarks`](../../muster-benchmarks), which is not published.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="check-cost-dark.svg">
  <img alt="Time of a passing check by number of rules. With 20 rules, RuleChain.check takes 382 ns and muster.check(target) 406 ns, against 276 ns for calling the same rules by hand." src="check-cost-light.svg">
</picture>

## Budget

Muster checks and orders the rules of a chain once, when the chain is created, or for
`muster.check(target)` on the first check of each input class. It does no reflection while it
checks. A check that passes is held to this budget, against the same rules called by hand:

| | Measured | Budget |
|---|---|---|
| Fixed cost of `RuleChain.check` | about 10 ns | at most 20 ns |
| Cost that `RuleChain.check` adds with 20 rules | about 106 ns | at most 150 ns |
| Fixed cost of `muster.check(target)` | about 19 ns | at most 35 ns |
| Cost that `muster.check(target)` adds with 20 rules | about 130 ns | at most 235 ns |
| Memory a check allocates | 24 bytes per rule, plus up to 40 | at most 24 bytes per rule, plus 48 |
| Fixed cost of the Spring Boot starter, against calling the same binding by hand | about 123 ns and 96 bytes | at most 200 ns and 128 bytes |

Most of what a rule adds is the context it receives: each rule gets its own, 24 bytes, since it
carries the rule's id. For a passing check the memory figures are exact; the JIT does not vary them.

Most of what the starter costs is Spring AOP's own: a proxy whose advice does nothing, measured
the same way, takes about 85 ns, and the 96 bytes are what Spring AOP allocates for each call: the
array of arguments, a cache key and the method invocation. The starter's advice itself looks up the method's rules in two maps and allocates
nothing.

The numbers hold for the machine described below. A faster machine gives smaller numbers; a change
to Muster is compared with the budget on the same machine.

## Results

The rules do next to nothing, so that the numbers show Muster rather than the rules. Each has a
`check` of its own, as in an application, so the call to it stays virtual. Each cell is the median
of the five forks' average time per check, with the range of the forks within 10% of the median,
and the median of the memory the forks allocate per check.

A check that passes:

| Rules | By hand (baseline) | `RuleChain.check` | `muster.check(target)` |
|---|---|---|---|
| 0 | 1.3 ns (1.3–1.3), 0 B | 11.5 ns (11.4–11.6), 16 B | 19.9 ns (19.9–20.1), 16 B |
| 1 | 11.2 ns (11.0–11.2), 0 B | 11.9 ns (11.8–11.9), 0 B | 21.3 ns (21.2–21.3), 0 B |
| 5 | 66.1 ns (65.6–66.9), 0 B | 102.6 ns (101.9–109.7), 144 B | 117.8 ns (114.7–124.8), 144 B |
| 20 | 275.7 ns (266.5–289.5), 0 B | 381.8 ns (379.4–403.9), 520 B | 405.5 ns (394.5–410.5), 520 B |

With a single rule the JIT inlines the call to it and optimizes the context away, so the check
allocates nothing.

A check of 20 rules that fails, caught as the caller would catch it. The violated rules are spread
evenly, and the last rule is always one of them:

| Violated rules | `FAIL_FAST` | `COLLECT_ALL` |
|---|---|---|
| 1 | 21.2 ns (21.0–21.7), 24 B | 144.1 ns (143.3–153.3), 120 B |
| 5 | 273 ns (263–277), 256 B | 905 ns (874–935), 1,264 B |

`FAIL_FAST` stops at the first violated rule, so with five of them it runs only four rules. The
exception has no stack trace. In this run every fork of a failing check allocated the same.

There is no transaction, so that the numbers show Muster.

Now and then a fork settles on other compiled code than the others, by hand as well as in Muster,
and nearly always slower code. In this run, three of the 120 forks ended more than 10% from the
median of their benchmark, and the ranges above leave them out: `RuleChain.check` with no rule
(12.9 ns), and one fork of each position of the multi-threaded benchmark, the first bucket slower
(47.8 ns) and the second faster (41.9 ns) than the others. The median of five forks keeps such a fork from deciding the published number.

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

[CONTRIBUTING.md](../../CONTRIBUTING.md#benchmarks) says how to run the benchmarks and publish
their results here.
