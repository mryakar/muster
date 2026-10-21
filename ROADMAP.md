# Roadmap

Each release is tracked as a GitHub milestone.

| Version | Scope |
|---|---|
| 0.1.0 | Core: rule contract, plain Java API, `@RequiresRule`, fail-fast and collect-all modes, error model |
| 0.2.0 | Spring Boot starter: AOP, transaction ordering, `ProblemDetail`, rule switches from configuration, message resolution |
| 0.3.0 | Collection and batch checks, self-invocation detection, observability, actuator endpoint |
| 0.4.x | Documentation site, hardening and performance work, then release candidates |
| 1.0.0 | API freeze |

1.0.0 is not tied to a date: the API must have been used in at least one real project, with no
breaking changes in the last few minor releases.
