# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `muster.binding(type)` builds a `Binding`: the rules it lists, and for a rule target the rules that bind to its class by type, less the rules it excludes, in a mode and under a name of its own. A binding for a final type prepares its rules when it is built; for another type, on the first check of each class. A listed rule cannot require an excluded rule; a rule bound by type that requires one is left out and logged.
- `muster.check(target, mode)` checks a rule target in a mode other than the Muster's, without a second Muster. The rules of a class are still bound once.
- `RuleContext.forRule(Class)` and `RuleContext.builder(Class)`, which read the rule id from `@Rule`, so a rule's test does not repeat it.
- `MessageResolver` turns the code and the client arguments of a violation into a message in the caller's locale, at the edge of the application; it never sees internal arguments. `MessageResolver.ofTemplates(lookup)` resolves messages from templates of your own, in which `{name}` stands for a client argument, written with `String.valueOf`. A name that is not a client argument stays as it is, `{{` and `}}` write a single brace, and apostrophes need no escaping. Without a template, the message is the code.
- The violation category `FORBIDDEN`, for a caller who may not act on the data a request refers to, such as a task that only its owner may close.

### Changed

- **Breaking:** A chain runs its rules in the order it lists them, not in the order of their ids, so cheap rules can run first. Before each listed rule it runs the rules that rule requires and that have not run yet, each after its own requirements, ordered the same way: listed ones in the order they are listed, the others in the order of their ids. `muster.check(target)` still runs rules in the order of their ids. The same chain may now report another violation in `FAIL_FAST` mode, and the violations of `COLLECT_ALL` mode may come in another order.
- **Breaking:** `Category` has a new constant, `FORBIDDEN`, listed first so that the categories follow the order of the status codes they stand for. A `switch` over `Category` without a `default` no longer compiles until it handles `FORBIDDEN`.
- **Breaking:** A violation code cannot contain whitespace, spaces such as the no-break space, control characters, format characters such as the zero-width space, or unpaired surrogates, which break message keys or hide in them unseen. A code is otherwise free, for example `VEHICLE_NAME_TAKEN`. A violation serialized by 0.1.0 with such a code no longer deserializes.
- **Breaking:** An argument name, client or internal, follows the same rule as a code and cannot contain a brace either, so that a message template can refer to every client argument as `{name}`. A violation serialized by 0.1.0 with such an argument name no longer deserializes.
- The Javadoc of `RuleContext.locale()` no longer suggests comparing names in the caller's locale, which lets the same name pass for one language and fail for another; it says to normalize stored data in one fixed locale.
- When rules that require a disabled rule are still enabled, Muster names all of them at once, also those that require it through other rules, so one change to the configuration fixes the setup.

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

[Unreleased]: https://github.com/mryakar/muster/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/mryakar/muster/releases/tag/v0.1.0
