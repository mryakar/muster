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
// The base conventions, and the quality gates of the projects that are tested and published:
// Spock tests, full coverage, mutation testing and strict API docs.
plugins {
  id("muster.java-base-conventions")
  `java-library`
  groovy
  jacoco
  id("info.solidsoft.pitest")
}

val libs = versionCatalogs.named("libs")

fun version(alias: String): String = libs.findVersion(alias).get().requiredVersion

// Internal packages are not exported, so the API docs leave them out. The source path lets the
// documented classes still refer to them.
tasks.withType<Javadoc>().configureEach {
  exclude("**/internal/**")
  (options as StandardJavadocDocletOptions).apply {
    addStringOption(
      "-source-path",
      sourceSets["main"].java.srcDirs.joinToString(File.pathSeparator),
    )
    addBooleanOption("Xdoclint:all", true)
    addBooleanOption("Xwerror", true)
  }
}

// Published to consumers: modules that require me.yakar.muster read org.jspecify, and
// null-aware tools need the annotations on their classpath.
dependencies { api(libs.findLibrary("jspecify").get()) }

testing {
  suites.withType<JvmTestSuite>().configureEach {
    useSpock(version("spock"))
    dependencies {
      implementation(platform(libs.findLibrary("groovy-bom").get()))
      implementation(platform(libs.findLibrary("junit-bom").get()))
      implementation(platform(libs.findLibrary("spock-bom").get()))
      runtimeOnly("org.junit.platform:junit-platform-launcher")
    }
  }
}

// Code is compiled for Java 21; -PtestJavaVersion runs the tests on a newer JVM.
val testJavaVersion = providers.gradleProperty("testJavaVersion").map(String::toInt).orElse(21)

tasks.withType<Test>().configureEach {
  javaLauncher = javaToolchains.launcherFor {
    languageVersion = testJavaVersion.map(JavaLanguageVersion::of)
  }
}

jacoco { toolVersion = version("jacoco") }

// Coverage counts every test suite, integration tests included.
val allExecutionData = fileTree(layout.buildDirectory.dir("jacoco")) { include("*.exec") }

tasks.jacocoTestReport {
  dependsOn(tasks.withType<Test>())
  executionData.setFrom(allExecutionData)
  reports { xml.required = true }
}

tasks.jacocoTestCoverageVerification {
  dependsOn(tasks.withType<Test>())
  executionData.setFrom(allExecutionData)
  violationRules {
    rule {
      listOf("INSTRUCTION", "BRANCH", "LINE").forEach {
        limit {
          counter = it
          minimum = "1.0".toBigDecimal()
        }
      }
    }
  }
}

tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }

pitest {
  pitestVersion = version("pitest")
  junit5PluginVersion = version("pitest-junit5-plugin")
  targetClasses = setOf("me.yakar.muster.*")
  threads = Runtime.getRuntime().availableProcessors()
  outputFormats = setOf("HTML", "XML")
  timestampedReports = false
  mutationThreshold = 95
  failWhenNoMutations = false
}

spotless {
  groovy {
    target("src/*/groovy/**/*.groovy")
    greclipse().configFile(layout.settingsDirectory.file("gradle/spotless/greclipse.properties"))
    licenseHeaderFile(
      layout.settingsDirectory.file("gradle/spotless/license-header.txt"),
      "(package|import)",
    )
  }
}
