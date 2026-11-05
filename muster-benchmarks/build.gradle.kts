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
  "JMH benchmarks of what Muster adds to a call, with and without Spring, and to the startup of an application. Not published."

dependencies {
  implementation(project(":muster-core"))
  // What the starter adds, with the oldest Spring Boot it supports.
  implementation(project(":muster-spring-boot-starter"))
  implementation(platform(libs.spring.boot.dependencies))
  // The advice the starter's is measured next to: method security and transactions; and the
  // observations a listener reports.
  implementation("org.springframework.security:spring-security-config")
  implementation("org.springframework.security:spring-security-core")
  implementation("org.springframework:spring-tx")
  implementation("io.micrometer:micrometer-observation")
  implementation(libs.jmh.core)
  // Reads JMH's results, which give each fork's measurements, for the published summary.
  implementation(libs.jackson.databind)
  annotationProcessor(libs.jmh.generator.annprocess)
}

// javac warns of every annotation that the JMH processor does not claim, such as @Rule.
tasks.compileJava { options.compilerArgs.add("-Xlint:-processing") }

// The applications whose startup StartupBenchmark measures: for each number of services, a package
// with an application class, five rules of a command interface, and the services, each with a
// command record of its own that two of its four methods take. Generated, since a thousand
// services are too many to keep in the sources; class files, which the scan for calls through
// `this` reads, as an application has them.
val generateStartupSources by tasks.registering {
  description = "Generates the applications whose startup StartupBenchmark measures."
  // The values of StartupBenchmark.services.
  val counts = listOf(0, 100, 1000)
  val directory = layout.buildDirectory.dir("generated/sources/startup/java")
  inputs.property("counts", counts)
  outputs.dir(directory)
  doLast {
    val root = directory.get().asFile
    root.deleteRecursively()
    for (count in counts) {
      val name = "me.yakar.muster.benchmark.startup.s$count"
      val dir = root.resolve(name.replace('.', '/')).apply { mkdirs() }
      fun write(file: String, text: String) =
        dir.resolve("$file.java").writeText("package $name;\n\n" + text.trimIndent() + "\n")
      write(
        "Application",
        """
          /** The application, with $count services. */
          @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
          @org.springframework.boot.autoconfigure.AutoConfigurationPackage
          @org.springframework.context.annotation.ComponentScan
          public class Application {}
          """,
      )
      write(
        "Command",
        """
          /** The input of every advised method. */
          public interface Command extends me.yakar.muster.rule.RuleTarget {}
          """,
      )
      for (rule in 1..5) {
        write(
          "Rule$rule",
          """
            /** A rule of every command, which passes. */
            @me.yakar.muster.rule.Rule(id = "s$count-rule-$rule")
            public final class Rule$rule implements me.yakar.muster.rule.BusinessRule<Command> {
              @Override
              public me.yakar.muster.rule.RuleResult check(
                  Command input, me.yakar.muster.rule.RuleContext ctx) {
                return me.yakar.muster.rule.RuleResult.passed();
              }
            }
            """,
        )
      }
      for (service in 0 until count) {
        write(
          "Command$service",
          """
            /** The command of Service$service. */
            public record Command$service(String name) implements Command {}
            """,
        )
        write(
          "Service$service",
          """
            /** A service with two methods that the starter checks and two that it does not. */
            @org.springframework.stereotype.Service
            public class Service$service {
              /** Saves. */
              public void save(Command$service command) {}

              /** Updates. */
              public void update(Command$service command) {}

              /** Counts. */
              public int count(int value) {
                return value + 1;
              }

              /** Names. */
              public String name() {
                return "Service$service";
              }
            }
            """,
        )
      }
    }
  }
}

sourceSets.main { java.srcDir(generateStartupSources) }

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
  mainClass = "me.yakar.muster.benchmark.report.PublishResults"
  val results = jmhResults
  val docs = layout.settingsDirectory.dir("docs/benchmarks")
  inputs.file(results)
  outputs.files(
    docs.file("results.json"),
    docs.file("results.csv"),
    docs.file("startup.csv"),
    docs.file("check-cost-light.svg"),
    docs.file("check-cost-dark.svg"),
  )
  argumentProviders.add { listOf(results.get().asFile.absolutePath, docs.asFile.absolutePath) }
}
