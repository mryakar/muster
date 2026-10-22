# Muster

> Does your request pass muster?

Business rules for layered Java applications, checked before your service method runs — inside the
transaction, outside your service code.

> [!NOTE]
> Muster is in early development and the API will change until 1.0.0. See the
> [roadmap](ROADMAP.md).

## Why

Service methods tend to start with a wall of checks: does this record exist, is that name already
taken, is the task in the right state. Muster moves each of those checks into its own rule class.
Rules run before the first line of the service method, inside its transaction, so adding a rule
never means editing the service.

Format checks on a single field (blank, negative, malformed) stay with Jakarta Bean Validation.
Muster is for rules that need the database and the context of the operation.

Running rules around the service method and inside its transaction is the job of the Spring Boot
starter, which arrives in 0.2.0. Until then, Muster checks rules through its plain Java API.

## Installation

Muster is on Maven Central. Import the BOM, which keeps the versions of the Muster modules in
line, and add the core without a version.

Gradle:

```kotlin
dependencies {
  implementation(platform("me.yakar:muster-bom:0.1.0"))
  implementation("me.yakar:muster-core")
}
```

Maven:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>me.yakar</groupId>
      <artifactId>muster-bom</artifactId>
      <version>0.1.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>me.yakar</groupId>
    <artifactId>muster-core</artifactId>
  </dependency>
</dependencies>
```

The core is a Java module. An application that is a module too reads it with
`requires me.yakar.muster;`. Each [release](https://github.com/mryakar/muster/releases) also
carries the jars, their signatures and an SBOM.

## Usage

A rule is a class with a stable id. It reports violations instead of throwing:

```java
@Rule(id = "vehicle.name-unique")
final class VehicleNameUnique implements BusinessRule<VehicleFields> {

  private final VehicleRepository vehicles;

  VehicleNameUnique(VehicleRepository vehicles) {
    this.vehicles = vehicles;
  }

  @Override
  public RuleResult check(VehicleFields input, RuleContext ctx) {
    return vehicles.existsByName(input.name())
        ? RuleResult.failed(ctx.violation().clientArg("name", input.name()).build())
        : RuleResult.passed();
  }
}
```

Build one `Muster` for the application, then check inputs against chains of rules:

```java
Muster muster = Muster.builder()
    .rules(new VehicleNameUnique(vehicles), new VehicleCodeFormat())
    .build();

RuleChain<CreateVehicle> create =
    muster.chain(CreateVehicle.class, VehicleNameUnique.class, VehicleCodeFormat.class);
create.check(command); // throws BusinessRuleViolationException if a rule is violated
```

An input that implements `RuleTarget` can skip the chain: every rule whose input type accepts it
binds by type.

```java
interface VehicleFields extends RuleTarget {
  String code();

  String name();
}

record CreateVehicle(String code, String name) implements VehicleFields {}

muster.check(new CreateVehicle("TR-1", "Atlas"));
```

Rules can require other rules with `@RequiresRule`, be switched off with `disable`, and stop at the
first violation (`CheckMode.FAIL_FAST`, the default) or report them all (`CheckMode.COLLECT_ALL`).
A rule that needs the time takes a `Clock` in its constructor, so that its tests can fix it.

## Performance

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/benchmarks/check-cost-dark.svg">
  <img alt="Time of a passing check by number of rules. With 20 rules, RuleChain.check takes 369 ns and muster.check(target) 383 ns, against 274 ns for calling the same rules by hand." src="docs/benchmarks/check-cost-light.svg">
</picture>

Muster checks and orders the rules of a chain once, when the chain is created, and does no
reflection while it checks. A passing `RuleChain.check` costs about 11 ns more than calling the
same rules by hand; with 20 rules it adds about 100 ns, most of it the 24-byte context each rule
receives. A check that fails throws an exception without a stack trace and takes 0.4 to 1 µs,
depending on the mode and the violations. The numbers come from JMH on a 2013 laptop CPU; [the
benchmarks](docs/benchmarks/README.md) give the method, every result and the budget a change is
held to.

## Modules

| Module | Purpose |
|---|---|
| `muster-core` | Rule contract, engine and plain Java API. Its only dependency is the JSpecify annotations. |
| `muster-spring-boot-starter` | Spring Boot integration: AOP, transaction ordering, `ProblemDetail` responses. Coming in 0.2.0. |
| `muster-bom` | Aligns the versions of the Muster modules. |
| `muster-examples` | Compiled examples used by the documentation and the integration tests. |
| `muster-benchmarks` | JMH benchmarks of what Muster adds to a call. Not published. |

## Requirements

- Java 21 or later
- Spring Boot 4 for the starter, from 0.2.0

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
