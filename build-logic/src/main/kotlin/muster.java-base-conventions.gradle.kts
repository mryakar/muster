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
// What every Java project of the build shares: the Java version, strict compiler warnings and the
// format. Projects that are tested and published add the quality gates of muster.java-conventions.
plugins {
  java
  id("com.diffplug.spotless")
}

val libs = versionCatalogs.named("libs")

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

tasks.withType<JavaCompile>().configureEach {
  options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

spotless {
  java {
    target("src/*/java/**/*.java")
    googleJavaFormat(libs.findVersion("google-java-format").get().requiredVersion)
    licenseHeaderFile(
      layout.settingsDirectory.file("gradle/spotless/license-header.txt"),
      "(package|import|module|open|@)",
    )
  }
}
