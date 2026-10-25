# Muster

> Does your request pass muster?

Business rules for layered Java applications, checked before your service method runs — inside the
transaction, outside your service code.

> [!NOTE]
> Muster is in early development and the API will change until 1.0.0. See the
> [roadmap](ROADMAP.md). The latest release is 0.1.0, with the core only; this page describes
> 0.2.0, which adds the Spring Boot starter.

## Why

Service methods tend to start with a wall of checks: does this record exist, is that name already
taken, is the task in the right state. Muster moves each of those checks into its own rule class.
With the Spring Boot starter, rules run before the first line of the service method, inside its
transaction, so adding a rule never means editing the service, and a violated rule reaches the
client as problem details in its language.

Format checks on a single field (blank, negative, malformed) stay with Jakarta Bean Validation.
Muster is for rules that need the database and the context of the operation.

## Installation

Muster is on Maven Central. Import the BOM, which keeps the versions of the Muster modules in
line, and add the Spring Boot starter, or the core alone without Spring, without a version.

Gradle:

```kotlin
dependencies {
  implementation(platform("me.yakar.muster:muster-bom:0.2.0"))
  implementation("me.yakar.muster:muster-spring-boot-starter") // or muster-core without Spring
}
```

Maven:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>me.yakar.muster</groupId>
      <artifactId>muster-bom</artifactId>
      <version>0.2.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>me.yakar.muster</groupId>
    <artifactId>muster-spring-boot-starter</artifactId> <!-- or muster-core without Spring -->
  </dependency>
</dependencies>
```

The starter needs Spring Boot 4.0.8 or later. The core is a Java module: an application that is a
module too reads it with `requires me.yakar.muster;`. From 0.2.0 on, each
[release](https://github.com/mryakar/muster/releases) also carries the jars and POMs, their
signatures and SBOMs; those of 0.1.0, which has the core only, are on Maven Central only.
[SECURITY.md](SECURITY.md#verifying-a-release) says how to verify them. Used without Maven or
Gradle, the core also needs `org.jspecify:jspecify` to compile.

## Usage with Spring Boot

A rule is a class with a stable id. It reports violations instead of throwing. The starter finds
rule classes in the packages of the application, as it finds entities, and creates them as beans,
so they need no `@Component` and get their dependencies through the constructor:

```java
@Rule(id = "vehicle.name-unique")
class VehicleNameUnique implements BusinessRule<VehicleFields> {

  private final VehicleRepository vehicles;

  VehicleNameUnique(VehicleRepository vehicles) {
    this.vehicles = vehicles;
  }

  @Override
  public RuleResult check(VehicleFields input, RuleContext ctx) {
    return vehicles.existsByName(input.name())
        ? RuleResult.failed(
            ctx.violation("vehicle.name-taken")
                .clientArg("name", input.name())
                .target("name")
                .category(Category.CONFLICT)
                .build())
        : RuleResult.passed();
  }
}
```

A command that implements `RuleTarget` is checked by every rule whose input type accepts it. The
public methods of a `@Service` that take one check its rules before they run, inside their
transaction, with no call to Muster. Such a method takes the command as its only parameter: one
that takes a rule target among other parameters stops the application.

```java
interface VehicleFields extends RuleTarget {
  String name();
}

record CreateVehicle(String name) implements VehicleFields {}

@Service
class VehicleService {

  @Transactional
  public void create(CreateVehicle command) { … } // VehicleNameUnique runs first

  @Transactional
  @Rules(value = VehicleNotInUse.class, mode = CheckMode.COLLECT_ALL)
  public void delete(Long id) { … } // the listed rules, on any bean
}
```

In a Spring MVC application, a violated rule becomes problem details (RFC 9457). The status
follows the category: 403 for `FORBIDDEN`, 404 for `NOT_FOUND`, 409 for `CONFLICT`, and 422 for
`INVALID_STATE`, no category, or violations of different categories. The message comes from the
message source of the application, in the language of the request, with `{name}` taking the client
argument of that name:

```properties
# messages_tr.properties
vehicle.name-taken={name} adı kullanımda
```

```
POST /vehicles                Accept-Language: tr-TR
409 Conflict                  Content-Type: application/problem+json
{
  "title": "Conflict",
  "status": 409,
  "instance": "/vehicles",
  "violations": [
    {
      "code": "vehicle.name-taken",
      "message": "Atlas adı kullanımda",
      "target": "name",
      "category": "CONFLICT",
      "args": { "name": "Atlas" }
    }
  ]
}
```

Internal arguments, added with `internalArg`, never leave the application; violations are logged at
DEBUG with them. A controller advice of the application that names
`BusinessRuleViolationException` in an `@ExceptionHandler` replaces the starter's handler, and can
inject its `MessageResolver` bean to resolve the messages.

Rules learn who makes the call from Spring Security, when the application has it, and the locale
from `LocaleContextHolder`; a `Supplier<Principal>` or `Supplier<Locale>` bean replaces either.
Rules can require other rules with `@RequiresRule`. Configuration changes the rest without a new
build:

| Property | Default | |
|---|---|---|
| `muster.mode` | `fail-fast` | `fail-fast` stops at the first violated rule; `collect-all` reports every violation |
| `muster.rules.<rule id>.enabled` | `true` | Switches a rule off, also as an environment variable such as `MUSTER_RULES_VEHICLE_NAMEUNIQUE_ENABLED` |
| `muster.problem-details.mixed-status` | `422` | The status of violations of different categories, from 400 to 499 |
| `muster.transaction.reorder-advisor` | `true` | Moves the transaction advice ahead of Muster's, so that rules run inside the transaction, unless `@EnableTransactionManagement` has an order of its own |

The Javadoc of `MusterAutoConfiguration` describes the starter in full.

## Usage without Spring

The core needs no framework. Build one `Muster` for the application, then check inputs against
chains of rules, in the order you list them, or by type:

```java
Muster muster = Muster.builder()
    .rules(new VehicleNameUnique(vehicles), new VehiclePlateFormat())
    .caller(() -> CurrentCall.principal()) // your own ThreadLocal, for example
    .locale(() -> CurrentCall.locale())
    .disable(disabledRules()) // ids from your configuration, for example
    .build();

RuleChain<CreateVehicle> create =
    muster.chain(CreateVehicle.class, VehiclePlateFormat.class, VehicleNameUnique.class);
create.check(command); // throws BusinessRuleViolationException if a rule is violated

muster.check(command); // every rule whose input type accepts CreateVehicle
```

What the starter does for you is up to you:

- **Switching rules off:** read the ids from the environment or your configuration and pass them to
  `disable`. `build()` fails if an id is unknown or an enabled rule requires a disabled one.
- **Transactions:** check as the first step inside the transaction of the operation, so that a rule
  reads the data that the operation then changes.
- **Messages:** `MessageResolver.ofResourceBundles(locale -> ResourceBundle.getBundle("com.example.messages", locale))`
  resolves the message of a violation from your resource bundles, with the same `{name}`
  placeholders. Load the bundles in your own module, so that they are found on the module path.
- **HTTP:** map `Violation.category()` to a status yourself, as the starter does: 403 for
  `FORBIDDEN`, 404 for `NOT_FOUND`, 409 for `CONFLICT`, and 422 otherwise. Send the code, the message
  and `clientArgs()`, never the violation itself.
- **Requirements across types:** a rule that only some inputs need can still be required. To check
  that a vehicle exists before its name is compared, give `VehicleExists` the same input type as
  `VehicleNameUnique`, `VehicleFields`, and let it pass for inputs without an id, such as
  `CreateVehicle`, while it looks up those that have one, such as an `UpdateVehicle` that also
  implements an interface with `id()`. `VehicleNameUnique` requires it.

A rule that needs the time takes a `Clock` in its constructor, so that its tests can fix it.

## Performance

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/benchmarks/check-cost-dark.svg">
  <img alt="Time of a passing check by number of rules. With 20 rules, RuleChain.check takes 371 ns and muster.check(target) 382 ns, against 264 ns for calling the same rules by hand." src="docs/benchmarks/check-cost-light.svg">
</picture>

Muster checks and orders the rules of a chain once, when the chain is created, and does no
reflection while it checks. A passing `RuleChain.check` costs about 12 ns more than calling the
same rules by hand; with 20 rules it adds about 110 ns, most of it the 24-byte context each rule
receives. A check that fails throws an exception without a stack trace and takes 0.4 to 1 µs,
depending on the mode and the violations. The Spring Boot starter adds about 120 ns to a call of an
advised service method, most of it the cost of a Spring AOP proxy. The numbers come from JMH on a
2013 laptop CPU; [the benchmarks](docs/benchmarks/README.md) give the method, every result and the
budget a change is held to.

## Modules

| Module | Purpose |
|---|---|
| `muster-core` | Rule contract, engine and plain Java API. Its only dependency is the JSpecify annotations. |
| `muster-spring-boot-starter` | Spring Boot integration: rules around service methods and inside their transaction, problem details, messages, configuration. |
| `muster-bom` | Aligns the versions of the Muster modules. |
| `muster-examples` | Compiled examples used by the documentation and the integration tests. Not published. |
| `muster-benchmarks` | JMH benchmarks of what Muster adds to a call. Not published. |

## Requirements

- Java 21 or later
- Spring Boot 4.0.8 or later for the starter

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
