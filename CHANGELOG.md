# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Violations that keep client arguments, which reach messages and responses, apart from internal arguments, which never leave the application.
- `BusinessRuleViolationException`, thrown once with every violation; its message lists codes only.
- Rule results: a rule either passes or fails with one or more violations.
- The rule contract: a rule implements `BusinessRule<T>`, carries a stable id with `@Rule`, and can opt out of a call with `appliesTo`.
- A rule context that tells a rule who makes the call and in which locale.
- `Muster`, the engine: rules are registered as instances or created by a `RuleFactory`, and every rule is checked when Muster is built. `muster.chain(...)` prepares a `RuleChain` once; each check runs the rules that apply and throws a single exception with all violations.
- `@RequiresRule`: a rule runs after the rules it requires, and only if they passed. Chains add required rules they do not list. Each rule class declares its own requirements; a rule that would lose them to inheritance is rejected.
- Binding rules by type: an input that implements `RuleTarget` is checked with `muster.check(input)` by every enabled rule whose input type accepts its class.
- Switching rules off by id with `disable`, without changing code.
- Check modes: `FAIL_FAST`, the default, stops at the first violated rule; `COLLECT_ALL` reports the violations of every rule. Muster has a mode, and a chain can override it with `withMode`.
- The API is split by audience: `me.yakar.muster` for the calling side, `me.yakar.muster.rule` for writing rules, `me.yakar.muster.spi` for integrations.
