/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// Compares the public API of the module with that of the release in apiBaseline, for binary and
// source compatibility as japicmp classifies it, as part of check. A break passes only once a
// commit
// since that release declares it, with ! or a BREAKING CHANGE footer, and only before 1.0 or in a
// new major version, as SemVer allows it.
import me.champeau.gradle.japicmp.JapicmpTask

plugins { java }

val baselineVersion = providers.gradleProperty("apiBaseline")

// The baseline release from Maven Central. A configuration of the project itself would resolve the
// project's own coordinates to the project; a detached one has an identity of its own.
val apiBaselineArchives =
  configurations
    .detachedConfiguration(
      dependencies.create("${project.group}:${project.name}:${baselineVersion.get()}"),
    )
    .apply { isTransitive = false }

// Whether a commit since the baseline release declares a breaking change, read from git so that a
// local build and CI apply the same rule, with the types the commit check allows. The release tag
// must be in the clone.
val breakingDeclared: Provider<Boolean> = baselineVersion.flatMap { baseline ->
  val log = providers.exec {
    commandLine("git", "log", "--no-show-signature", "--format=%B%x00", "v$baseline..HEAD")
    isIgnoreExitValue = true
  }
  log.result.zip(log.standardOutput.asText.zip(log.standardError.asText, ::Pair)) {
    result,
    (messages, error) ->
    if (result.exitValue != 0) {
      throw GradleException(
        "Cannot read the commits since v$baseline, the release in apiBaseline; a clone needs the " +
          "tags and the history since it, as git fetch --tags gives. git said: " +
          error.trim(),
      )
    }
    val subject = Regex("^(feat|fix|docs|refactor|chore|test|build|ci|perf)(\\([a-z0-9-]+\\))?!: ")
    val footer = Regex("^BREAKING[ -]CHANGE: ", RegexOption.MULTILINE)
    messages.split('\u0000').any {
      subject.containsMatchIn(it.trim()) || footer.containsMatchIn(it)
    }
  }
}

// SemVer allows a break before 1.0 and in a new major version.
val breakAllowedByVersion = baselineVersion.map { baseline ->
  val major = project.version.toString().substringBefore('.').toInt()
  major == 0 || major > baseline.substringBefore('.').toInt()
}

val japicmp =
  tasks.register<JapicmpTask>("japicmp") {
    description = "Compares the public API with that of the release in apiBaseline."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    oldArchives.from(apiBaselineArchives)
    oldClasspath.from(configurations.compileClasspath)
    newArchives.from(tasks.jar)
    newClasspath.from(configurations.compileClasspath)
    packageExcludes.add("*.internal*")
    // The report lists every change; the task fails on a binary or a source incompatible one.
    onlyModified = true
    onlyBinaryIncompatibleModified = false
    val breaksFail =
      breakAllowedByVersion.zip(breakingDeclared) { allowed, declared -> !(allowed && declared) }
    failOnModification = breaksFail
    failOnSourceIncompatibility = breaksFail
    htmlOutputFile = layout.buildDirectory.file("reports/japicmp/index.html")
    mdOutputFile = layout.buildDirectory.file("reports/japicmp/report.md")
  }

tasks.check { dependsOn(japicmp) }
