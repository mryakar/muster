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
plugins {
  id("muster.java-conventions")
  id("muster.publishing-conventions")
}

description = "Spring Boot integration for Muster."

// The Spring Boot to build and test against: the oldest supported, another one with
// -PspringBootVersion, or the catalog's newest with -PspringBootVersion=latest. The published POM
// depends on the oldest and imports no platform, so the application's Spring Boot manages the
// versions.
val springBootVersion =
  providers
    .gradleProperty("springBootVersion")
    .map { if (it == "latest") libs.versions.spring.boot.latest.get() else it }
    .getOrElse(libs.versions.spring.boot.asProvider().get())
val springBoot = "${libs.spring.boot.dependencies.get().module}:$springBootVersion"

dependencies {
  api(project(":muster-core"))
  api(libs.spring.boot.autoconfigure)
  compileOnly(platform(springBoot))
  // Optional: the caller comes from Spring Security when the application has it.
  compileOnly("org.springframework.security:spring-security-core")
  // Optional: rules run inside the transaction when the application has Spring's transaction
  // support.
  compileOnly("org.springframework:spring-tx")
  // Optional: violations become problem details in a Spring MVC application.
  compileOnly("org.springframework:spring-web")
  // Annotations that keep problem details whole whatever the JSON settings; Jackson reads them.
  compileOnly("com.fasterxml.jackson.core:jackson-annotations")
  // Optional: checks and rules are observed when the application has an ObservationRegistry.
  compileOnly("io.micrometer:micrometer-observation")
  // Optional: the actuator endpoint "muster" when the application has the actuator.
  compileOnly("org.springframework.boot:spring-boot-actuator")
  compileOnly("org.springframework.boot:spring-boot-actuator-autoconfigure")
}

// Specs change JVM-wide state, such as the default locale, Muster's logger and the security
// context, so they rely on running one at a time, as Spock does unless told otherwise.
testing {
  suites {
    named<JvmTestSuite>("test") {
      dependencies {
        implementation(platform(springBoot))
        implementation("org.springframework.boot:spring-boot-starter-test")
        implementation("org.springframework.boot:spring-boot-starter-validation")
        implementation("org.springframework.boot:spring-boot-starter-webmvc")
        implementation("org.springframework.boot:spring-boot-starter-webmvc-test")
        implementation("org.springframework.security:spring-security-core")
        implementation("org.springframework:spring-tx")
        // Compiles the code that ahead-of-time processing generates, to start an application from
        // it.
        implementation("org.springframework:spring-core-test")
        implementation("io.micrometer:micrometer-observation-test")
        implementation("org.springframework.boot:spring-boot-starter-actuator")
        implementation("org.spockframework:spock-spring")
      }
    }
    register<JvmTestSuite>("integrationTest") {
      dependencies {
        implementation(project())
        implementation(platform(springBoot))
        implementation("org.spockframework:spock-spring")
        implementation("org.springframework.boot:spring-boot-starter-jdbc")
        implementation("org.springframework.boot:spring-boot-starter-test")
        implementation("org.springframework.boot:spring-boot-starter-webmvc")
        implementation("org.springframework.boot:spring-boot-testcontainers")
        implementation("org.testcontainers:testcontainers-postgresql")
        runtimeOnly("org.postgresql:postgresql")
      }
      targets.configureEach { testTask.configure { shouldRunAfter(tasks.test) } }
    }
  }
}

tasks.check { dependsOn(testing.suites.named("integrationTest")) }

tasks.jar { manifest { attributes("Automatic-Module-Name" to "me.yakar.muster.spring") } }

// The reference of the configuration properties in the documentation, written from the metadata
// that IDEs read, so that the two never differ. A task writes it; check fails if it is out of date.
abstract class PropertyReference : DefaultTask() {

  @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val metadata: RegularFileProperty

  /** Returns the reference: an AsciiDoc table of the properties, in the order of the metadata. */
  protected fun render(): String {
    @Suppress("UNCHECKED_CAST")
    val json =
      groovy.json.JsonSlurper().parse(metadata.get().asFile) as Map<String, List<Map<String, Any?>>>
    val values =
      json["hints"].orEmpty().associate { hint ->
        hint["name"] as String to (hint["values"] as List<Map<String, Any?>>).map { it["value"] }
      }
    val rows =
      json.getValue("properties").joinToString("\n") { property ->
        val name = property["name"] as String
        // Each sentence as written: a passthrough keeps AsciiDoc from reading braces, asterisks or
        // underscores in it, and a cell ends at the next unescaped bar.
        val sentences =
          (property["description"] as String).split(Regex("(?<=\\.) (?=[A-Z])")).map {
            "pass:c[" + it.replace("]", "\\]").replace("|", "\\|") + "]"
          }
        val allowed = values[name]?.joinToString(", ", "Values: ", ".") { "`+$it+`" }
        val default =
          when (val value = property["defaultValue"]) {
            null -> ""
            is List<*> -> value.joinToString(", ") { "`+$it+`" }
            else -> "`+$value+`"
          }
        "|`+$name+`\n|$default\n|" + (sentences + listOfNotNull(allowed)).joinToString("\n") + "\n"
      }
    val header =
      listOf(
        "// Written by ./gradlew :muster-spring-boot-starter:propertyReference from the starter's",
        "// META-INF/spring-configuration-metadata.json: change that file, then run the task.",
        "[cols=\"2,1,5\"]",
        "|===",
        "|Property |Default |Description",
        "",
        "",
      )
    return header.joinToString("\n") + rows + "|===\n"
  }
}

abstract class WritePropertyReference : PropertyReference() {

  @get:OutputFile abstract val reference: RegularFileProperty

  @TaskAction
  fun write() {
    reference.get().asFile.writeText(render())
  }
}

abstract class CheckPropertyReference : PropertyReference() {

  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val reference: RegularFileProperty

  @TaskAction
  fun check() {
    if (reference.get().asFile.readText() != render()) {
      throw GradleException(
        "The property reference in the documentation is out of date: run " +
          "./gradlew :muster-spring-boot-starter:propertyReference",
      )
    }
  }
}

val propertyMetadata =
  layout.projectDirectory.file("src/main/resources/META-INF/spring-configuration-metadata.json")
val propertyReferencePage =
  layout.settingsDirectory.file("docs/modules/ROOT/partials/properties.adoc")

tasks.register<WritePropertyReference>("propertyReference") {
  description = "Writes the reference of the configuration properties in the documentation."
  metadata = propertyMetadata
  reference = propertyReferencePage
}

val checkPropertyReference =
  tasks.register<CheckPropertyReference>("checkPropertyReference") {
    description = "Fails if the reference of the configuration properties is out of date."
    metadata = propertyMetadata
    reference = propertyReferencePage
    // Nothing to write; a task with an up-to-date spec counts as having outputs, so it is checked
    // again only when the metadata or the reference changes.
    outputs.upToDateWhen { true }
    // After the task that writes the reference, when both run in one build.
    mustRunAfter("propertyReference")
  }

tasks.check { dependsOn(checkPropertyReference) }
