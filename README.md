# Muster

> Does your request pass muster?

Business rules for layered Java applications, checked before your service method runs — inside the
transaction, outside your service code.

> [!NOTE]
> Muster is in early development. Nothing has been released yet and the API will change until 1.0.0.
> See the [roadmap](ROADMAP.md).

## Why

Service methods tend to start with a wall of checks: does this record exist, is that name already
taken, is the task in the right state. Muster moves each of those checks into its own rule class.
Rules run before the first line of the service method, inside its transaction, so adding a rule
never means editing the service.

Format checks on a single field (blank, negative, malformed) stay with Jakarta Bean Validation.
Muster is for rules that need the database and the context of the operation.

## Modules

| Module | Purpose |
|---|---|
| `muster-core` | Rule contract, engine and plain Java API. No runtime dependencies. |
| `muster-spring-boot-starter` | Spring Boot integration: AOP, transaction ordering, `ProblemDetail` responses. |
| `muster-bom` | Aligns the versions of the Muster modules. |
| `muster-examples` | Compiled examples used by the documentation and the integration tests. |

## Requirements

- Java 21 or later
- Spring Boot 4 for the starter

## Building

```bash
./gradlew build
```

The build checks formatting, runs unit and integration tests and verifies coverage. Integration
tests start PostgreSQL with Testcontainers, so they need a running Docker daemon. Mutation testing
runs separately:

```bash
./gradlew pitest
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Report security issues as described in
[SECURITY.md](SECURITY.md).

## License

Muster is licensed under the [Apache License 2.0](LICENSE).
