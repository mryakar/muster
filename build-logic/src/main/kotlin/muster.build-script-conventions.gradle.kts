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
// Formats the Gradle build scripts and the examples, and keeps their license headers, for the root
// project.
plugins { id("com.diffplug.spotless") }

val libs = versionCatalogs.named("libs")
val licenseHeader = layout.settingsDirectory.file("gradle/spotless/license-header.txt")

spotless {
  // The examples are a build of their own, outside the projects that format their sources.
  java {
    target("muster-examples/*/src/*/java/**/*.java")
    googleJavaFormat(libs.findVersion("google-java-format").get().requiredVersion)
    licenseHeaderFile(licenseHeader, "(package|import|module|open|@)")
  }
  kotlinGradle {
    target(
      "*.gradle.kts",
      "*/*.gradle.kts",
      "muster-examples/*/*.gradle.kts",
      "build-logic/src/main/kotlin/*.gradle.kts",
    )
    ktfmt(libs.findVersion("ktfmt").get().requiredVersion).googleStyle()
    licenseHeaderFile(
      licenseHeader,
      "(plugins|pluginManagement|dependencyResolutionManagement|//)",
    )
  }
}
