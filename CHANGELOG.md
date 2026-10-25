# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- The constraints of a `@Validated` method are checked before its rules, and before its transaction: the starter tells Spring Boot's method validation to add its advice ahead of the advice a proxy already has, unless the application defines the `MethodValidationPostProcessor` itself. `muster.validation.reorder-advisor=false` leaves the order to Spring, where the rules run before the constraints.

## [0.2.0] - 2026-10-25

### Added

- A Spring Boot starter, `me.yakar.muster:muster-spring-boot-starter`, released on Maven Central with the core and the BOM. It builds a `Muster` bean from the rule beans of the application and registers the `@Rule` classes in the application's packages as beans. It works with Spring Boot 4.0.8 and later; Spring Security, Spring's transactions and Spring MVC are optional.
- `@Rules` checks the input of a method before the method runs, against the rules it lists and, for a rule target, the rules bound to its class by type. The public methods of a `@Service` that take a rule target are checked without it. A method that cannot be checked stops the application at startup and the error says why.
- Rules run inside the transaction of their method: the starter orders its advice after the transaction advice, and refuses to start, or to create a bean later, when they would run outside it.
- `muster.rules.<rule id>.enabled=false` switches a rule off without a new build, also as an environment variable; `muster.mode` sets `fail-fast` or `collect-all`. A key that switches no rule, a property set to nothing, or either property next to a `Muster` bean of the application's own stops the application.
- Rules learn the caller from Spring Security and the locale from `LocaleContextHolder`; a `Supplier<Principal>` or `Supplier<Locale>` bean replaces either.
- A `MessageResolver` bean resolves the messages of violations from the message source of the application; `{name}` placeholders take the client arguments, and a code without a template is its own message.
- In a Spring MVC application, a `BusinessRuleViolationException` is answered with problem details (RFC 9457): 403 for `FORBIDDEN`, 404 for `NOT_FOUND`, 409 for `CONFLICT`, 422 otherwise, and `muster.problem-details.mixed-status` for violations of different categories. The `violations` property lists each violation's code, message, target, category and client arguments; the rule id and the internal arguments stay out. A controller advice of the application that handles the exception replaces it.
- `muster.binding(type)` builds a `Binding` of listed rules, rules bound by type and excluded rules, with a mode and a name of its own; `muster.check(target, mode)` checks a target in another mode than the Muster's.
- `MessageResolver`, in `me.yakar.muster.spi`, turns the code and the client arguments of a violation into a message in the caller's locale. `MessageResolver.ofTemplates(lookup)` uses templates of your own and `MessageResolver.ofResourceBundles(…)` resource bundles that your module loads; neither falls back to the default locale of the JVM.
- `RuleContext.forRule(Class)` and `RuleContext.builder(Class)`, which read the rule id from `@Rule`.
- The violation category `FORBIDDEN`, for a caller who may not act on the data a request refers to.
- Example applications in `muster-examples`, one in plain Java on the module path and one with Spring Boot. They use the published artifacts, so they also test a release before it is published.
- The measured cost of the starter, in `docs/benchmarks`: about 120 ns and 96 bytes a call, most of it Spring AOP's proxy. The published figures are the median of five forks.

### Changed

- **Breaking:** A chain runs its rules in the order it lists them, not in the order of their ids, each after the rules it requires. `muster.check(target)` still runs rules in the order of their ids. A chain may now report another violation in `FAIL_FAST` mode, and the violations of `COLLECT_ALL` mode may come in another order.
- **Breaking:** `Category` has a new constant, `FORBIDDEN`, listed first. A `switch` over `Category` without a `default` no longer compiles until it handles it.
- **Breaking:** A violation code cannot contain whitespace, control or format characters, or unpaired surrogates, and an argument name cannot contain a brace either. A violation serialized by 0.1.0 with such a code or name no longer deserializes.
- When rules that require a disabled rule are still enabled, Muster names all of them at once, also those that require it through other rules.
- A violation without client arguments, or without internal ones, allocates no map for them, so a failing check allocates less.
- The Javadoc of `RuleContext.locale()` says to normalize stored data in one fixed locale, instead of comparing names in the caller's locale.

### Fixed

- A violation read with Java serialization must have a valid rule id; a stream that holds a violation no rule could build fails with an `InvalidObjectException`.
- Muster logs the rules it binds to a class after it has bound them, no longer while it holds the lock of the map that keeps them, so a slow logging backend holds up no other class and a logger that checks the same class no longer fails with `Recursive update`.
- The purls in the SBOM of `muster-core` no longer carry a `project_path` qualifier, which no published artifact has.

## [0.1.0] - 2026-10-23

### Added

- Violations that keep client arguments, which reach messages and responses, apart from internal arguments, which never leave the application.
- `BusinessRuleViolationException`, thrown once per check with the violations found; its message lists codes only.
- Rule results: a rule either passes or fails with one or more violations.
- Violation categories (`NOT_FOUND`, `CONFLICT`, `INVALID_STATE`), an optional target field, and a severity. Internal arguments are dropped when a violation goes through Java serialization.
- The rule contract: a rule implements `BusinessRule<T>`, carries a stable id with `@Rule`, and can opt out of a call with `appliesTo`.
- A rule context that tells a rule who makes the call and in which locale. `RuleContext.forRule(id)` creates one for testing a rule on its own. `toBuilder()` derives the context of the same rule for another call.
- `Muster`, the engine: rules are registered as instances or created by a `RuleFactory`, and every rule is validated when Muster is built. `muster.chain(...)` prepares a `RuleChain` once; each check runs the rules that apply and throws a single exception with the violations, as the check mode decides.
- `@RequiresRule`: a rule runs after the rules it requires, and only if they passed. Chains add required rules they do not list. Each rule class declares its own requirements; a rule that would lose them to inheritance is rejected.
- Binding rules by type: an input that implements `RuleTarget` is checked with `muster.check(input)` by every enabled rule whose input type accepts its class. Muster warns when no rule binds to a target.
- Switching rules off by id with `disable`, without changing code.
- Check modes: `FAIL_FAST`, the default, stops at the first violated rule; `COLLECT_ALL` reports the violations of every rule. Muster has a mode, and a chain can override it with `withMode`.
- `muster-core` depends only on the JSpecify annotations, which carry the API's null contract.
- Releases of `me.yakar.muster:muster-core` and `me.yakar.muster:muster-bom` on Maven Central, signed, with sources, API docs and, for the core, a CycloneDX SBOM.
- The API is split by audience: `me.yakar.muster` for the calling side, `me.yakar.muster.rule` for writing rules, `me.yakar.muster.spi` for integrations.
- JMH benchmarks in `muster-benchmarks`, which is not published, measure what a check adds to a call, also from many threads at once. `./gradlew jmh` runs them.
- The measured cost of a check and the budget it is held to, in `docs/benchmarks`, with a chart in the README. A passing check of 20 rules adds about 100 ns over calling them by hand.

[Unreleased]: https://github.com/mryakar/muster/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/mryakar/muster/releases/tag/v0.2.0
[0.1.0]: https://github.com/mryakar/muster/releases/tag/v0.1.0
