# Contributing to Muster

Thank you for your interest in Muster. The project is in early development, so please open an issue
to discuss a change before you start working on it.

## Building

Muster needs JDK 21, 21.0.8 or later from an OpenJDK build such as Temurin or Corretto, which the
nullness check needs, and a running Docker daemon for the integration tests.

| Command | What it does |
|---|---|
| `./gradlew build` | Formatting check, unit tests, integration tests, coverage verification |
| `./gradlew spotlessApply` | Formats the code and adds license headers |
| `./gradlew pitest` | Mutation testing |
| `./gradlew check -PtestJavaVersion=25` | Runs the tests on another JDK (the code is still compiled for 21) |
| `./gradlew :muster-spring-boot-starter:check -PspringBootVersion=latest` | Tests the starter against the newest Spring Boot, `spring-boot-latest` in `gradle/libs.versions.toml`, or against the version given (the build uses the oldest supported, `spring-boot`) |
| `./gradlew publishToMavenLocal`, then `.github/scripts/run-examples.sh` | Runs the example applications against what the build publishes (see [Releases](#releases)) |

The documentation site is built with [Antora](https://antora.org/) from `docs/`, with Node.js 24:
in `docs/`, `npm ci --ignore-scripts` once, since no package needs its install scripts, then
`npm run build`, which writes the site to `docs/build/site`. A warning, such as a broken cross
reference, fails the build. The site is in English, in `docs/`, and in Turkish, in `docs/tr/`, with
the same pages under the same paths: a change to a page changes both. A contributor who does not
write Turkish copies the English text into the Turkish page, and the maintainer translates it.
`.github/scripts/check-docs-languages.sh` checks that both have the same pages, navigation and
version keys. Every code sample in the documentation is included from `muster-examples` through the
symbolic links in `docs/modules/ROOT/examples`, which `docs/tr/` links to as well; on Windows,
enable symbolic links in Git (`core.symlinks`) before cloning, or the links are checked out as files
and the build fails on the first include. A sample is a tagged region of an example,
`// tag::check-all[]` to `// end::check-all[]`, named after the code it holds rather than the page
that shows it, and included with `tag=check-all,indent=0`; a tag that is not found fails the build.
An output that an example compares with what it gets, such as a JSON answer or a log, is a file of
its own, included whole. The examples check what the guides say, so a sample that changes what
Muster does changes the example's assertions too. The reference of the starter's properties is
written from its `spring-configuration-metadata.json`, which IDEs read too: after changing the
metadata, run `./gradlew :muster-spring-boot-starter:propertyReference`; `check` fails while the
reference is out of date. The site serves the Javadoc of each version under `api/`, which
`.github/scripts/javadoc-site.sh <site directory>` puts there: the javadoc jars of each GitHub
Release, and the Javadoc of the checked-out commit as `next`.

## Code

- Format with `./gradlew spotlessApply` before committing. Java follows google-java-format; Groovy
  tests use the same indentation.
- Every source file starts with the Apache-2.0 license header. Spotless adds it, except to
  `package-info.java` and `module-info.java`, where it is written by hand.
- `muster-core` depends only on the JSpecify annotations. Do not add runtime dependencies.
- Packages are `@NullMarked` (JSpecify). NullAway checks the main code of the core and the starter
  against the annotations as it compiles, and fails the build where a value that may be null
  reaches a place that does not allow it.

## Tests

- Unit tests are written with [Spock](https://spockframework.org/) in `src/test/groovy`.
- Properties that hold for every input, such as the order of a chain for any graph of rules, are
  tested with [jqwik](https://jqwik.net/) in classes named `…Properties`, next to the
  specifications. Each run tries other inputs; a failure prints its seed, which
  `@Property(seed = "…")` on the property reruns.
- The test that starts an application from the code that ahead-of-time processing generates is a
  JUnit Jupiter test, in Groovy too: it needs Spring's `@CompileWithForkedClassLoader`, a Jupiter
  extension that Spock does not run, to load the generated code next to the package-private `@Bean`
  methods it calls.
- Integration tests live in the `integrationTest` source set and use Spring Boot, Testcontainers and
  PostgreSQL.
- Every change comes with tests. The build requires 100% instruction, branch and line coverage.
- Mutation testing (PIT) guards against tests that execute code without checking it;
  `./gradlew pitest` fails when it kills fewer than 95% of the mutations.

## Benchmarks

`muster-benchmarks` measures with [JMH](https://github.com/openjdk/jmh) what Muster adds to a call,
against the same rules called by hand, and what the starter adds to the startup of an application.
It is not published, and the build only compiles it, with the applications whose startup it measures,
which it generates.

```bash
./gradlew jmh
```

The run takes over an hour and writes `muster-benchmarks/build/results/jmh/results.json`, with the
time and the allocation per call, or per start, of each fork. `--args` selects benchmarks and overrides their
settings, except the result file and format, as in `./gradlew jmh --args="CheckBenchmark -f 1"`. For
numbers worth comparing, run `jmh` on its own, on a machine plugged in and otherwise idle, with a
fixed CPU frequency.

`./gradlew jmhReport` copies the results of the last run to `docs/benchmarks`, writes the median of
each benchmark's forks to `results.csv` there, and of the starts to `startup.csv`, and draws the
chart that the README shows. It refuses results that lack a benchmark method or parameter, or that
ran in another mode or time unit, or with other forks, iterations or threads, than a benchmark
declares. A change that affects performance is compared with the budget in
[docs/benchmarks](docs/benchmarks/README.md), on the same machine.

## Dependencies

Gradle verifies every dependency against the SHA-256 checksums in
`gradle/verification-metadata.xml`. What IDEs download to show sources is the one exception, in
`<trusted-artifacts>`, which Gradle keeps when it rewrites the file. The sources and javadoc jars of
the dependencies and the source zip of the Gradle distribution are trusted by name, since they are
read and never run. The metadata of the Groovy that Gradle bundles is trusted too, for that one
version: an IDE reads it on the way to the sources of each bundled module, and no build resolves it.
When the Gradle wrapper moves to a version that bundles another Groovy, which the `groovy-*.jar`
files in the `lib` directory of the distribution name, put that version in the rule. When you add or
update a dependency, update the file with the tasks that resolve it, and review what changed.
`--refresh-dependencies` makes Gradle read parent and BOM POMs again, which it would otherwise take
from its cache without recording them. The build resolves the oldest Spring Boot the starter
supports; the second command records the newest, which CI tests the starter against:

```bash
./gradlew --write-verification-metadata sha256 --refresh-dependencies --no-configuration-cache --rerun-tasks build pitest publishToMavenLocal
./gradlew --write-verification-metadata sha256 --refresh-dependencies --no-configuration-cache --rerun-tasks :muster-spring-boot-starter:check -PspringBootVersion=latest
```

Writing the metadata adds entries but removes none: to drop the versions that nothing resolves any
more, delete their entries and run the commands again, which add back what the build needs. Next to
the file, `gradle/osv-scanner.toml` lists the known vulnerabilities that scanners, OpenSSF
Scorecard's among them, find in it and that the build cannot remove yet, each with why it does not
apply and when it goes; remove an entry when its versions leave the metadata.

The examples have a file of their own, `muster-examples/gradle/verification-metadata.xml`. Their
Spring Boot application uses the Spring Boot version of `gradle/libs.versions.toml` and the
versions that Spring Boot manages, such as those of H2 and JUnit; the tests of the plain Java
application use the JUnit version of the same file. When either changes, update the examples' file
too:

```bash
./gradlew publishToMavenLocal
./gradlew -p muster-examples --write-verification-metadata sha256 --refresh-dependencies test run -PmusterVersion="$(sed -n 's/^version=//p' gradle.properties)"
```

Gradle drops the comments of a file it rewrites; keep the one on the trusted Muster artifacts.
Dependabot updates neither file, so its pull requests need the same steps.

The starter is built against the oldest Spring Boot it supports, `spring-boot` in
`gradle/libs.versions.toml`, and its published POM requires that version, so Dependabot leaves it
alone. Raise it by hand, preferably in a pull request of its own, when the starter needs something
that only a newer Spring Boot has, or when the oldest has a known vulnerability: change
`spring-boot` and the minimum that README.md and the documentation state, update both
verification-metadata files as above, and add the change to CHANGELOG.md. `spring-boot-latest`, the
newest Spring Boot that CI tests, is moved by hand too.

## Commits

Commits follow [Conventional Commits](https://www.conventionalcommits.org/):

```text
<type>(<optional-scope>)!: <description>
```

Allowed types: `feat`, `fix`, `docs`, `refactor`, `chore`, `test`, `build`, `ci`, `perf`. Mark
breaking changes with `!` or a `BREAKING CHANGE:` footer. Keep commits small; each commit should
contain only related changes. CI checks every commit message in a pull request;
`.github/scripts/check-commit-messages.sh main HEAD` checks those of your branch before you push.

## Pull requests

- `main` is protected; all changes go through pull requests.
- Pull requests are merged with **rebase and merge** only, so every commit lands on `main` as-is.
  Rebase your branch onto `main` instead of merging `main` into it.
- `check` compares the public API of the core and the starter with the release in `apiBaseline` of
  `gradle.properties`, and fails on a change that breaks binary or source compatibility as japicmp
  classifies it, with a report in `build/reports/japicmp`. Before 1.0, and in a new major version,
  such a change passes once a commit since that release declares it with `!` or a
  `BREAKING CHANGE` footer, which the build reads from git; it needs the release tags and the
  history since them, which a full clone has.
- SonarQube Cloud analyzes each pull request and shows its quality gate on it. The gate is advice,
  not a required check. Pull requests from forks and from Dependabot are not analyzed, since they
  get no token.

## Releases

The maintainer releases a version from a `vX.Y.Z` tag; the release workflow uploads it to Maven
Central and publishes it on GitHub. The release commit sets the version in `gradle.properties`,
dates the section of the version in `CHANGELOG.md`, and releases the documentation: it drops the
`prerelease` key of `docs/antora.yml` and `docs/tr/antora.yml` and sets `muster-version` in both.
The commit that starts the next version puts the key back in both. Once Maven Central serves the
release, it also sets `apiBaseline` in `gradle.properties` to the release and writes the checksums
of its jars into the verification metadata, since the build compares the API with them. A
pre-release, such as `1.0.0-rc.1`, has no version on the site and changes neither the documentation
keys nor `apiBaseline`.
[SECURITY.md](SECURITY.md#verifying-a-release) says how to verify a release.

To check what a build would publish, publish it to the local repository and run the examples in
`muster-examples`, which use Muster the way applications do: one without Spring, on the module path,
and a Spring Boot application that is called over HTTP. The script runs them twice, with Gradle's
module metadata and with the POMs only:

```bash
./gradlew publishToMavenLocal
.github/scripts/run-examples.sh
```

## License

By contributing, you agree that your contributions are licensed under the
[Apache License 2.0](LICENSE).
