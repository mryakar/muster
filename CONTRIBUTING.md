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

## Dependencies

Gradle verifies every dependency against the SHA-256 checksums in
`gradle/verification-metadata.xml`. When you add or update a dependency, update the file with the
tasks that resolve it, and review what changed. `--refresh-dependencies` makes Gradle read parent
and BOM POMs again, which it would otherwise take from its cache without recording them:

```bash
./gradlew --write-verification-metadata sha256 --refresh-dependencies --no-configuration-cache --rerun-tasks build pitest publishToMavenLocal
```

Dependabot does not update the file, so its pull requests need the same step.

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

## Releasing

Maintainers release a version in these steps:

1. A pull request sets the version in `gradle.properties` (for example `0.1.0`) and turns the
   changelog's `Unreleased` section into a section for that version.
2. After it is merged, a `vX.Y.Z` tag on that commit starts the release workflow. A maintainer
   approves the run in the `release` environment.
3. The workflow checks that the tag matches the version, builds, runs the consumer test in
   `consumer-test`, and uploads the release to the Maven Central Portal. It drafts a GitHub Release
   with the artifacts, signatures, SBOM and release notes.
4. A maintainer checks the upload and publishes it in the Central Portal. Publishing cannot be
   undone. Then they publish the GitHub Release.
5. A pull request moves the version to the next snapshot.

To check the artifacts locally, publish them to the local repository and run the consumer test:

```bash
./gradlew publishToMavenLocal
./gradlew -p consumer-test run -PmusterVersion=0.1.0-SNAPSHOT
```

## License

By contributing, you agree that your contributions are licensed under the
[Apache License 2.0](LICENSE).
