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
plugins { id("muster.java-base-conventions") }

description =
  "JMH benchmarks of what Muster adds to a call, with and without Spring. Not published."

dependencies {
  implementation(project(":muster-core"))
  // What the starter adds, with the oldest Spring Boot it supports.
  implementation(project(":muster-spring-boot-starter"))
  implementation(platform(libs.spring.boot.dependencies))
  implementation(libs.jmh.core)
  // Reads JMH's results, which give each fork's measurements, for the published summary.
  implementation(libs.jackson.databind)
  annotationProcessor(libs.jmh.generator.annprocess)
}

// javac warns of every annotation that the JMH processor does not claim, such as @Rule.
tasks.compileJava { options.compilerArgs.add("-Xlint:-processing") }

// Each task copies it to a local value: a lambda that read the script's own value would capture
// the script, which the configuration cache cannot store.
val jmhResults = layout.buildDirectory.file("results/jmh/results.json")

// --args selects benchmarks and overrides their settings, but not the result file or format: JSON
// keeps each fork's measurements and -prof gc the allocation per call, which jmhReport needs.
// jmhReport publishes only a full run with the settings that the classes declare.
tasks.register<JavaExec>("jmh") {
  description = "Runs the JMH benchmarks."
  group = "benchmark"
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass = "org.openjdk.jmh.Main"
  val results = jmhResults
  // A run measures the machine as it is now, so it never counts as up to date.
  doNotTrackState("Benchmark results depend on the machine, not only on the inputs")
  argumentProviders.add {
    listOf("-prof", "gc", "-rf", "json", "-rff", results.get().asFile.absolutePath)
  }
  doFirst { results.get().asFile.parentFile.mkdirs() }
}

tasks.register<JavaExec>("jmhReport") {
  description = "Publishes the medians of the last JMH run to docs/benchmarks, with a chart."
  group = "benchmark"
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass = "me.yakar.muster.benchmark.ResultsChart"
  val results = jmhResults
  val docs = layout.settingsDirectory.dir("docs/benchmarks")
  inputs.file(results)
  outputs.files(
    docs.file("results.json"),
    docs.file("results.csv"),
    docs.file("check-cost-light.svg"),
    docs.file("check-cost-dark.svg"),
  )
  argumentProviders.add { listOf(results.get().asFile.absolutePath, docs.asFile.absolutePath) }
}
