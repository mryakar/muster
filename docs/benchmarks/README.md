# Benchmarks

How much time and memory Muster costs, measured with [JMH](https://github.com/openjdk/jmh), the
benchmark tool of the JDK's own developers. The benchmarks live in
[`muster-benchmarks`](../../muster-benchmarks), which is not published.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="check-cost-dark.svg">
  <img alt="Time of a passing check by number of rules. With 20 rules, RuleChain.check takes 391 ns and muster.check(target) 398 ns, against 268 ns for calling the same rules by hand." src="check-cost-light.svg">
</picture>

## In short

If you only read one part of this page, read this. The numbers come from an old laptop (2013)
running at a fixed, low speed, so a modern machine is faster; what matters is how they compare.

- **A check costs a little more than calling your rules yourself.** With 20 rules, Muster adds
  about 0.12 microseconds (µs) to a call that already takes about 0.27 µs. A single database query
  takes hundreds of times longer, so in an application the difference does not show.
- **With Spring Boot, a service method pays about 0.13 µs more**, most of it for the proxy that
  Spring puts around the service. Next to a transaction, which costs about ten times as much, the
  starter adds less than a tenth of it, since the transaction needs the proxy too.
- **Checking a list in one call costs less than checking its elements one by one from five
  elements on:** 5 elements cost about 7% less, 100 elements about 27% less. A list of one element
  costs about one and a half times a check of the element.
- **The starter makes an application start slower:** about 0.27 seconds more with 100 services
  whose methods it checks, and 0.7 seconds more with 1,000, beyond what the proxies of the services
  cost. The proxies cost more than that, and Spring creates them anyway for an application that
  uses transactions or method security on its services. For services that have no proxy yet, the
  starter adds about 0.94 seconds with 100 services and 4.4 seconds with 1,000, proxies included.

## The words on this page

- **ns** is a nanosecond, a billionth of a second; **µs** a microsecond, a thousand ns; **ms** a
  millisecond, a million ns. **B** is a byte of memory that a call allocates, which the garbage
  collector frees again later; **MB** a million bytes.
- **By hand** is the baseline: the same rules called directly from Java code, with no Muster in
  between. What Muster *adds* is the difference to it.
- **A fork** is one run of a benchmark in a fresh JVM. Each benchmark runs in several forks, since
  the JIT, the compiler inside the JVM, does not compile the same code the same way every time.
- **The median** is the middle value: the forks' results are sorted, and the one in the middle is
  published. One unusually slow fork cannot decide it. Each cell gives the median, then in
  parentheses the range of the forks, leaving out a fork
  [far from the median](#forks-far-from-the-median).
- **A proxy** is an object that Spring puts in place of a service when the application starts, of a
  class it generates then, so that it can run code around each call of the service's methods: the
  **advice**. The starter's advice checks the rules of the method.

## Budget

Muster checks and orders the rules of a chain once, when the chain is created, or for
`muster.check(target)` on the first check of each input class. It does no reflection while it
checks. A change to Muster must stay within the budget, measured on the same machine:

| | Measured | Budget |
|---|---|---|
| Fixed cost of `RuleChain.check` | about 11 ns | at most 20 ns |
| Cost that `RuleChain.check` adds with 20 rules | about 123 ns | at most 150 ns |
| Fixed cost of `muster.check(target)` | about 20 ns | at most 35 ns |
| Cost that `muster.check(target)` adds with 20 rules | about 130 ns | at most 235 ns |
| Memory a check allocates | 24 bytes per rule, plus up to 40 | at most 24 bytes per rule, plus 48 |
| Fixed cost of the Spring Boot starter, against calling the same binding by hand | about 129 ns and 96 bytes | at most 200 ns and 128 bytes |
| Startup time the starter adds to an application with 100 checked services, beyond their proxies | about 265 ms | at most 400 ms |
| Startup time the starter adds to an application with 1,000 checked services, beyond their proxies | about 698 ms | at most 1,050 ms |

`muster.checkAll` has a goal rather than a budget: with one to five elements, at most twice the
time of checking each element with `muster.check`. It takes 1.56 times as long with one element,
and less than checking each one from five elements on.

Most of what a rule adds is the context it receives: each rule gets its own, 24 bytes, since it
carries the rule's id. For a passing check the memory figures are exact; the JIT does not vary them.

Most of what the starter costs is Spring AOP's own: a proxy whose advice does nothing, measured the
same way, takes about 85 ns, and the 96 bytes are what Spring AOP allocates for each call: the array
of arguments, a cache key and the method invocation. The starter's advice itself looks up the
method's rules in two maps and allocates nothing.

## Results

The rules do next to nothing, so that the numbers show Muster rather than the rules. Each has a
`check` of its own, as in an application, so the call to it stays virtual. Each cell is the median
of the five forks' average time per check, with the range of the forks within 10% of the median,
and the median of the memory the forks allocate per check.

### A check that passes

| Rules | By hand (baseline) | `RuleChain.check` | `muster.check(target)` |
|---|---|---|---|
| 0 | 1.3 ns (1.3–1.4), 0 B | 12.2 ns (12.0–12.3), 16 B | 21.4 ns (21.2–21.8), 16 B |
| 1 | 11.1 ns (11.0–11.3), 0 B | 12.5 ns (12.3–12.5), 0 B | 23.0 ns (22.7–23.0), 0 B |
| 5 | 65.4 ns (65.3–66.2), 0 B | 108.5 ns (104.8–111.6), 144 B | 117.1 ns (116.3–122.7), 144 B |
| 20 | 268.2 ns (258.2–270.8), 0 B | 390.9 ns (381.1–415.3), 520 B | 397.9 ns (393.4–419.3), 520 B |

With a single rule the JIT inlines the call to it and optimizes the context away, so the check
allocates nothing.

### A check that fails

A check of 20 rules that fails, caught as the caller would catch it. The violated rules are spread
evenly, and the last rule is always one of them:

| Violated rules | `FAIL_FAST` | `COLLECT_ALL` |
|---|---|---|
| 1 | 414 ns (410–431), 640 B | 482 ns (474–491), 792 B |
| 5 | 112 ns (111–115), 256 B | 726 ns (722–764), 1,264 B |

`FAIL_FAST`, the default, stops at the first violated rule, so with five of them it runs only four
rules. `COLLECT_ALL` runs every rule and reports every violation. The exception has no stack trace.
In this run every fork of a failing check allocated the same, except the slow fork named
[below](#forks-far-from-the-median), with 872 bytes.

The loop over the rules is a method of its own, too large for the JIT to inline, that returns the
violations it found; the small method that throws is inlined into the caller, which catches the
exception without unwinding a frame.

### Next to method security and a transaction

The starter's advice on a service method, against the same method with the same other advice that
checks the same rules with a binding by hand. Method security checks a role of the caller in a
`SecurityContext`; the transaction manager has no database, so that the numbers show the
interceptors and their order, not JDBC:

| Other advice | Rules | By hand | With the starter |
|---|---|---|---|
| None | 0 | 25.9 ns (25.0–26.7), 16 B | 155.0 ns (154.2–158.6), 112 B |
| None | 20 | 402.2 ns (384.9–404.8), 520 B | 551.7 ns (548.1–576.2), 616 B |
| Method security | 0 | 2,539 ns (2,502–2,562), 6,064 B | 2,575 ns (2,498–2,620), 6,064 B |
| Method security | 20 | 3,074 ns (2,940–3,173), 6,648 B | 3,169 ns (3,111–3,265), 6,600 B |
| Transaction | 0 | 1,143 ns (1,125–1,156), 712 B | 1,221 ns (1,215–1,249), 712 B |
| Transaction | 20 | 1,626 ns (1,586–1,678), 1,216 B | 1,718 ns (1,668–1,735), 1,216 B |
| Both | 0 | 3,808 ns (3,755–3,916), 6,696 B | 3,959 ns (3,816–4,225), 6,616 B |
| Both | 20 | 4,607 ns (4,473–4,634), 7,184 B | 4,807 ns (4,687–5,071), 7,152 B |

Next to method security, the starter adds between about 35 and 200 ns, no more than the forks of
one benchmark differ from one another. Next to a transaction it adds about 80 to 90 ns, less than a
tenth of the transaction. It adds no memory: with a transaction both columns allocate the same, and
with method security the memory varies from fork to fork by more than the starter could add. The proxy and the 96 bytes that Spring AOP allocates for each call are there anyway.

Spring Security is on the class path of every benchmark of the starter, and the starter reads the
caller from it on each call, so the numbers without other advice are not comparable one to one with
those of 0.2.0; the budget compares them on the same class path.

### A listener

`muster.check(target)` with the listeners of a Muster that the starter configures: none, one that
does nothing, and the starter's observations, with a registry whose handler takes every
observation:

| Listener | No rule | 20 rules |
|---|---|---|
| None | 40.2 ns (40.0–40.6), 16 B | 420.0 ns (416.4–434.6), 520 B |
| One that does nothing | 106.7 ns (106.0–110.4), 72 B | 2,118 ns (1,960–2,219), 576 B |
| Observations | 458.2 ns (451.1–466.6), 776 B | 9,943 ns (9,744–10,034), 15,040 B |

Without a listener, a check pays one null check, and each rule a test of a local that is null; it
takes longer here than in the first table because the starter's Muster reads the caller from Spring
Security and the locale from Spring's `LocaleContextHolder` on each check. A listener that does
nothing adds about 67 ns and 56 bytes to a check, and about 80 ns to each rule. The observations
add about 420 ns and 760 bytes to a check, and about 455 ns and 690 bytes to each rule.

### A collection

A collection checked against 20 rules: the rules called by hand on each element, each element on
its own with `muster.check`, or all at once with `muster.checkAll`:

| Elements | By hand | `muster.check` on each | `muster.checkAll` |
|---|---|---|---|
| 1 | 287.3 ns (286.2–301.0), 0 B | 402.2 ns (396.0–412.6), 520 B | 627.2 ns (620.6–631.8), 856 B |
| 5 | 1,480 ns (1,461–1,518), 0 B | 1,998 ns (1,994–2,095), 2,600 B | 1,849 ns (1,810–1,894), 3,448 B |
| 10 | 2,935 ns (2,923–3,006), 0 B | 3,991 ns (3,937–4,032), 5,200 B | 3,513 ns (3,467–3,616), 6,688 B |
| 100 | 29,495 ns (29,066–30,303), 0 B | 39,448 ns (39,239–43,008), 52,000 B | 28,840 ns (28,702–29,185), 65,096 B |

The rules do not override `checkAll`, so Muster checks each element against each rule itself, in a
loop of each rule over the elements. The chain is looked up, the listener asked and the rules walked
once for the whole collection, so from five elements on that takes less time than checking each
on its own; with 100 elements it is as fast as calling the rules by hand. With one element it takes
about one and a half times as long as a check of the element.

The memory is the context of each rule, which carries the position of the element, and for each
call an array of the positions of the elements and one that records which elements passed a rule.
No rule here requires another, so all of them share these two. A chain whose rules require others
allocates for each call one more array of the elements that passed for each rule that another
requires, and one of positions for each rule that requires others.

Measured on the same machine on the same day, 0.3.0 took 890 ns and 1,768 bytes with one element,
2.19 times its own check of the element (407 ns); 2,181 ns and 4,664 bytes with five; and 30,694 ns and 75,280
bytes with 100. Each rule had an array of its own for the elements that passed it, and one for the
positions it may check.

A rule that looks up its elements, as in a database, can override `checkAll` to do it with one query
instead of one for each element.

### Startup

How long an application takes to start, from `SpringApplication.run` until it is ready, in a fresh
JVM, as when it is deployed, though without a web server. Each fork starts the application once, so the classes are loaded and
the code runs before the JIT has compiled it. Each cell is the median of eleven forks, with their
range, and the memory the start allocates.

The application has 0, 100 or 1,000 services; each takes a command of its own in two of its four
methods, and five rules check every command. It starts in three ways: without Muster and without
proxies (*plain*), without Muster but with a proxy of each service whose advice does nothing
(*proxied*), and with the starter:

| Services | Plain | Proxied | With the starter |
|---|---|---|---|
| 0 | 828 ms (793–887), 37.1 MB | 862 ms (826–894), 38.5 MB | 1,006 ms (968–1,085), 42.9 MB |
| 100 | 1,020 ms (988–1,097), 44.6 MB | 1,697 ms (1,629–1,797), 95.6 MB | 1,962 ms (1,877–2,042), 106.0 MB |
| 1,000 | 2,032 ms (1,918–2,181), 114.6 MB | 5,721 ms (5,499–6,047), 580.9 MB | 6,419 ms (6,189–6,539), 642.6 MB |

To start checking the methods of a service, Spring puts a proxy around it, a class it generates
when the application starts; generating the classes is most of the difference between *plain* and
*proxied*. An application whose services already have proxies, for transactions or method security,
pays for them with or without Muster, so the budget counts what the starter adds beyond them: about
144 ms with no service, for the starter's own configuration, from loading its classes to finding
the rules and creating Muster, 265 ms with 100 services, and 698 ms with 1,000. That is where the starter looks up the rules of each
method, binds them, and reads the class file of each service to find calls through `this`. Reading
the class files takes little time: with that scan switched off (`muster.self-invocation=off`), 1,000
services started in 6,410 ms, within the range of the forks, and allocated about 25 MB less.
An application whose services have no proxy yet gets them from the starter as well: against
*plain*, the starter adds 178 ms with no service, 943 ms with 100 services and 4,387 ms with 1,000.

The two applications without Muster import an auto-configuration that configures nothing, so that
all three start through the same machinery of Spring Boot. The memory, in millions of bytes, is what
JMH's GC profiler counts for the threads it sees: only roughly what a start allocates, but counted
the same way for all three, so the columns compare.

### Forks far from the median

Now and then a fork settles on other compiled code than the others, by hand as well as in Muster,
and nearly always slower code. In this run, eight of the 300 forks that measure an average time
ended more than 10% from the median of their benchmark, all slower, and the ranges above leave them
out: one calling 5 rules with `RuleChain.check` (143.7 ns) and one calling none (14.7 ns); one of a
failing check with one violated rule in `COLLECT_ALL` (549.0 ns); two of the starter's binding by
hand, without other advice (472.2 ns) and with both (5,138 ns), with 20 rules; two of a collection
of five elements, by hand (1,634 ns) and with `checkAll` (2,089 ns); and one of the multi-threaded
benchmark, whose results are only in `results.csv`, checking the class bound second to a shared
bucket of the chain map (56.2 ns). None of the 99 forks of a start did. The median of the forks keeps such a fork from deciding the
published number.

The raw results are in [`results.json`](results.json), the medians, ranges and the forks more than
10% from the median in [`results.csv`](results.csv), and those of the starts in
[`startup.csv`](startup.csv). `./gradlew jmhReport` writes them and the chart; the tables and the
image description here are updated by hand.

## How it was measured

- Intel Core i7-4700HQ (4 cores, 8 threads, 2013), on mains power, frequency fixed at 2.4 GHz:
  the `performance` governor, turbo off
- Linux 7.0, Amazon Corretto 21.0.11, JMH 1.37, Spring Boot 4.0.8 for the starter, with the
  Spring Security and Micrometer that it manages
- For a call, five forks, each with five one-second warm-up iterations and five one-second
  measurements; the published time of a benchmark is the median of its forks' averages
- For a start, eleven forks, each measuring a single start with no warm-up
- Allocation from JMH's GC profiler (`-prof gc`)
- A fork more than 10% from the median of its benchmark, as when it settles on other compiled code
  than the others, stays out of the published range and is named above

## Running the benchmarks

[CONTRIBUTING.md](../../CONTRIBUTING.md#benchmarks) says how to run the benchmarks and publish
their results here.
