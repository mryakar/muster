# Contributing to Muster

Thank you for your interest in Muster. The project is in early development, so please open an issue
to discuss a change before you start working on it.

## Building

Muster needs JDK 21 and a running Docker daemon for the integration tests.

| Command | What it does |
|---|---|
| `./gradlew build` | Formatting check, unit tests, integration tests, coverage verification |
| `./gradlew spotlessApply` | Formats the code and adds license headers |
| `./gradlew pitest` | Mutation testing |
| `./gradlew check -PtestJavaVersion=25` | Runs the tests on another JDK (the code is still compiled for 21) |
| `./gradlew :muster-spring-boot-starter:check -PspringBootVersion=latest` | Tests the starter against the newest Spring Boot, `spring-boot-latest` in `gradle/libs.versions.toml`, or against the version given (the build uses the oldest supported, `spring-boot`) |

## Code

- Format with `./gradlew spotlessApply` before committing. Java follows google-java-format; Groovy
  tests use the same indentation.
- Every source file starts with the Apache-2.0 license header. Spotless adds it, except to
  `package-info.java` and `module-info.java`, where it is written by hand.
- `muster-core` depends only on the JSpecify annotations. Do not add runtime dependencies.
- Packages are `@NullMarked` (JSpecify).

## Tests

- Unit tests are written with [Spock](https://spockframework.org/) in `src/test/groovy`.
- Integration tests live in the `integrationTest` source set and use Spring Boot, Testcontainers and
  PostgreSQL.
- Every change comes with tests. The build requires 100% instruction, branch and line coverage.
- Mutation testing (PIT) guards against tests that execute code without checking it.

## Benchmarks

`muster-benchmarks` measures with [JMH](https://github.com/openjdk/jmh) what Muster adds to a call,
against the same rules called by hand. It is not published, and the build only compiles it.

```bash
./gradlew jmh
```

The run takes about half an hour and writes `muster-benchmarks/build/results/jmh/results.json`, with
the time and the allocation per call of each fork. `--args` selects benchmarks and overrides their settings,
except the result file and format, as in `./gradlew jmh --args="CheckBenchmark -f 1"`. For numbers
worth comparing, run `jmh` on its own, on a machine plugged in and otherwise idle, with a fixed CPU
frequency.

`./gradlew jmhReport` copies the results of the last run to `docs/benchmarks`, writes the median of
each benchmark's forks to `results.csv` there, and draws the chart that the README shows. It refuses
results that lack a benchmark method or parameter, or that ran with other forks, iterations or
threads than a benchmark declares. A change that affects performance is compared with the budget in
[docs/benchmarks](docs/benchmarks/README.md), on the same machine.

## Dependencies

Gradle verifies every dependency against the SHA-256 checksums in
`gradle/verification-metadata.xml`. When you add or update a dependency, update the file with the
tasks that resolve it, and review what changed. `--refresh-dependencies` makes Gradle read parent
and BOM POMs again, which it would otherwise take from its cache without recording them:

```bash
./gradlew --write-verification-metadata sha256 --refresh-dependencies --no-configuration-cache --rerun-tasks build pitest publishToMavenLocal
```

The consumer test has a file of its own, `consumer-test/gradle/verification-metadata.xml`, and its
Spring Boot application uses the Spring Boot version of `gradle/libs.versions.toml`. When that
version changes, update the consumer test's file too:

```bash
./gradlew publishToMavenLocal
./gradlew -p consumer-test --write-verification-metadata sha256 --refresh-dependencies run -PmusterVersion="$(sed -n 's/^version=//p' gradle.properties)"
```

Gradle drops the comments of a file it rewrites; keep the one on the trusted Muster artifacts.
Dependabot updates neither file, so its pull requests need the same steps.

## Commits

Commits follow [Conventional Commits](https://www.conventionalcommits.org/):

```text
<type>(<optional-scope>)!: <description>
```

Allowed types: `feat`, `fix`, `docs`, `refactor`, `chore`, `test`, `build`, `ci`, `perf`. Mark
breaking changes with `!` or a `BREAKING CHANGE:` footer. Keep commits small; each commit should
contain only related changes. CI checks every commit message in a pull request.

## Pull requests

- `main` is protected; all changes go through pull requests.
- Pull requests are merged with **rebase and merge** only, so every commit lands on `main` as-is.
  Rebase your branch onto `main` instead of merging `main` into it.

## Releases

The maintainer releases a version from a `vX.Y.Z` tag; the release workflow uploads it to Maven
Central and publishes it on GitHub. [SECURITY.md](SECURITY.md#verifying-a-release) says how to verify a release.

To check what a build would publish, publish it to the local repository and run the consumer test,
which uses Muster the way applications do: one without Spring, on the module path, and a Spring
Boot application that it calls over HTTP:

```bash
./gradlew publishToMavenLocal
.github/scripts/consumer-test.sh
```

## License

By contributing, you agree that your contributions are licensed under the
[Apache License 2.0](LICENSE).
