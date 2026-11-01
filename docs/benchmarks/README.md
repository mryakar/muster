# Benchmarks

What Muster adds to a call, measured with [JMH](https://github.com/openjdk/jmh). The benchmarks
live in [`muster-benchmarks`](../../muster-benchmarks), which is not published.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="check-cost-dark.svg">
  <img alt="Time of a passing check by number of rules. With 20 rules, RuleChain.check takes 391 ns and muster.check(target) 402 ns, against 260 ns for calling the same rules by hand." src="check-cost-light.svg">
</picture>

## Budget

Muster checks and orders the rules of a chain once, when the chain is created, or for
`muster.check(target)` on the first check of each input class. It does no reflection while it
checks. A check that passes is held to this budget, against the same rules called by hand:

| | Measured | Budget |
|---|---|---|
| Fixed cost of `RuleChain.check` | about 11 ns | at most 20 ns |
| Cost that `RuleChain.check` adds with 20 rules | about 131 ns | at most 150 ns |
| Fixed cost of `muster.check(target)` | about 19 ns | at most 35 ns |
| Cost that `muster.check(target)` adds with 20 rules | about 141 ns | at most 235 ns |
| Memory a check allocates | 24 bytes per rule, plus up to 40 | at most 24 bytes per rule, plus 48 |
| Fixed cost of the Spring Boot starter, against calling the same binding by hand | about 128 ns and 96 bytes | at most 200 ns and 128 bytes |

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
| 0 | 1.3 ns (1.3–1.3), 0 B | 12.3 ns (11.6–12.7), 16 B | 20.7 ns (20.6–20.7), 16 B |
| 1 | 11.0 ns (11.0–11.0), 0 B | 12.6 ns (12.5–12.8), 0 B | 22.4 ns (22.0–22.5), 0 B |
| 5 | 66.0 ns (65.7–67.8), 0 B | 107.1 ns (101.8–111.8), 144 B | 116.5 ns (115.9–121.2), 144 B |
| 20 | 260.3 ns (258.9–266.4), 0 B | 391.2 ns (379.7–422.9), 520 B | 401.7 ns (387.0–434.6), 520 B |

With a single rule the JIT inlines the call to it and optimizes the context away, so the check
allocates nothing.

A check of 20 rules that fails, caught as the caller would catch it. The violated rules are spread
evenly, and the last rule is always one of them:

| Violated rules | `FAIL_FAST` | `COLLECT_ALL` |
|---|---|---|
| 1 | 683 ns (671–715), 720 B | 758 ns (744–768), 872 B |
| 5 | 267 ns (264–273), 256 B | 932 ns (915–984), 1,264 B |

`FAIL_FAST` stops at the first violated rule, so with five of them it runs only four rules. The
exception has no stack trace. In this run every fork of a failing check allocated the same.

With one violated rule, after 19 that pass, a failing check is slower and allocates more than in
0.2.0: measured on the same machine on the same day, 0.2.0 took 582 ns and 640 bytes with
`FAIL_FAST`, and 691 ns and 800 bytes with `COLLECT_ALL`. Why is not known yet; a check that passes
is not affected.

The checks above have no transaction and no method security, so that the numbers show Muster.

### Next to method security and a transaction

The starter's advice on a service method, against the same method with the same other advice that
checks the same rules with a binding by hand. Method security checks a role of the caller in a
`SecurityContext`; the transaction manager has no database, so that the numbers show the
interceptors and their order, not JDBC:

| Other advice | Rules | By hand | With the starter |
|---|---|---|---|
| None | 0 | 26.2 ns (24.0–28.0), 16 B | 154.1 ns (152.1–157.3), 112 B |
| None | 20 | 395.1 ns (368.2–431.3), 520 B | 548.4 ns (533.4–562.0), 616 B |
| Method security | 0 | 2,630 ns (2,514–2,760), 6,112 B | 2,596 ns (2,498–2,761), 6,112 B |
| Method security | 20 | 3,120 ns (2,896–3,236), 6,616 B | 3,200 ns (3,075–3,300), 6,616 B |
| Transaction | 0 | 1,154 ns (1,127–1,168), 712 B | 1,210 ns (1,191–1,227), 712 B |
| Transaction | 20 | 1,596 ns (1,530–1,642), 1,216 B | 1,723 ns (1,685–1,736), 1,216 B |
| Both | 0 | 3,832 ns (3,783–4,155), 6,712 B | 4,052 ns (3,952–4,442), 6,648 B |
| Both | 20 | 4,625 ns (4,449–4,874), 7,152 B | 4,758 ns (4,648–5,000), 7,112 B |

Next to other advice, the starter adds between nothing measurable and about 220 ns, no more than
the forks with method security differ from one another, and no memory: with a transaction both
columns allocate the same, and with method security the memory varies from fork to fork by up to
about 400 bytes, more than the starter could add. The proxy and the 96 bytes that Spring AOP
allocates for each call are there anyway.

Spring Security is on the class path of every benchmark of the starter, and the starter reads the
caller from it on each call, so the numbers without other advice are not comparable one to one with
those of 0.2.0; the budget compares them on the same class path.

### A listener

`muster.check(target)` with the listeners of a Muster that the starter configures: none, one that
does nothing, and the starter's observations, with a registry whose handler takes every
observation:

| Listener | No rule | 20 rules |
|---|---|---|
| None | 38.7 ns (38.2–39.5), 16 B | 420.4 ns (412.3–427.5), 520 B |
| One that does nothing | 104.7 ns (103.8–105.5), 72 B | 2,101 ns (2,082–2,155), 608 B |
| Observations | 468.9 ns (465.5–476.0), 848 B | 10,485 ns (10,284–10,552), 16,552 B |

Without a listener, a check pays one null check; it takes longer here than in the first table
because the starter's Muster reads the caller from Spring Security and the locale from Spring's
`LocaleContextHolder` on each check. A listener
that does nothing adds about 66 ns and 56 bytes to a check, and about 80 ns to each rule. The
observations add about 430 ns and 830 bytes to a check, and about 480 ns and 760 bytes to each
rule.

### A collection

A collection checked against 20 rules, each element on its own with `muster.check`, or all at once
with `muster.checkAll`:

| Elements | By hand | `muster.check` on each | `muster.checkAll` |
|---|---|---|---|
| 1 | 288.7 ns (287.2–294.2), 0 B | 397.5 ns (397.0–407.1), 520 B | 1,606 ns (1,494–1,638), 2,568 B |
| 10 | 3,003 ns (2,948–3,076), 0 B | 4,038 ns (3,938–4,349), 5,200 B | 7,492 ns (7,470–7,629), 16,360 B |
| 100 | 28,932 ns (28,340–30,002), 0 B | 39,641 ns (39,256–40,994), 52,000 B | 64,959 ns (63,726–65,760), 155,280 B |

The rules do not override `checkAll`, so Muster checks each element against each rule itself: with
100 elements, about 650 ns and 1,550 bytes for each element, against about 400 ns and 520 bytes for
checking each on its own. A rule that looks up its elements, as in a database, can override
`checkAll` to do it with one query instead of one for each element.

Now and then a fork settles on other compiled code than the others, by hand as well as in Muster,
and nearly always slower code. In this run, four of the 285 forks ended more than 10% from the
median of their benchmark, and the ranges above leave them out: `RuleChain.check` with no rule
(15.1 ns), and three forks of the multi-threaded benchmark, whose results are only in
`results.csv`: one checking the class bound first to a shared bucket of the chain map, slower
(52.3 ns), and two checking the class bound second, one faster (40.0 ns) and one slower (52.8 ns)
than the others. The median of five forks keeps such a fork from deciding the published number.

The raw results are in [`results.json`](results.json), and the medians, ranges and the forks more
than 10% from the median in [`results.csv`](results.csv). `./gradlew jmhReport` writes both and the
chart; the tables and the image descriptions here are updated by hand.

## How it was measured

- Intel Core i7-4700HQ (4 cores, 8 threads, 2013), on mains power, frequency fixed at 2.4 GHz:
  the `performance` governor, turbo off
- Linux 7.0, Amazon Corretto 21.0.11, JMH 1.37, Spring Boot 4.0.8 for the starter, with the
  Spring Security and Micrometer that it manages
- Five forks, each with five one-second warm-up iterations and five one-second measurements; the
  published time of a benchmark is the median of its forks' averages
- Allocation from JMH's GC profiler (`-prof gc`)
- A fork more than 10% from the median of its benchmark, as when it settles on other compiled code
  than the others, stays out of the published range and is named above

## Running the benchmarks

[CONTRIBUTING.md](../../CONTRIBUTING.md#benchmarks) says how to run the benchmarks and publish
their results here.
