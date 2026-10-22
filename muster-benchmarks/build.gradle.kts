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

description = "JMH benchmarks of what Muster adds to a call. Not published."

dependencies {
  implementation(project(":muster-core"))
  implementation(libs.jmh.core)
  annotationProcessor(libs.jmh.generator.annprocess)
}

// javac warns of every annotation that the JMH processor does not claim, such as @Rule.
tasks.compileJava { options.compilerArgs.add("-Xlint:-processing") }

// Runs every benchmark; --args selects some and overrides their settings, as in
// --args="CheckBenchmark -f 1", except the result file and format. The results are written as CSV,
// with the allocation per call.
tasks.register<JavaExec>("jmh") {
  description = "Runs the JMH benchmarks."
  group = "benchmark"
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass = "org.openjdk.jmh.Main"
  val results = layout.buildDirectory.file("results/jmh/results.csv")
  // A run measures the machine as it is now, so it never counts as up to date.
  doNotTrackState("Benchmark results depend on the machine, not only on the inputs")
  argumentProviders.add {
    listOf("-prof", "gc", "-rf", "csv", "-rff", results.get().asFile.absolutePath)
  }
  doFirst { results.get().asFile.parentFile.mkdirs() }
}

// Copies the results of the last run to docs/benchmarks and draws the chart that the README shows.
tasks.register<JavaExec>("jmhReport") {
  description = "Publishes the results of the last JMH run to docs/benchmarks, with a chart."
  group = "benchmark"
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass = "me.yakar.muster.benchmark.ResultsChart"
  val results = layout.buildDirectory.file("results/jmh/results.csv")
  val docs = layout.settingsDirectory.dir("docs/benchmarks")
  inputs.file(results)
  outputs.dir(docs)
  args(results.get().asFile.absolutePath, docs.asFile.absolutePath)
}
