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
plugins { id("muster.java-conventions") }

description = "Spring Boot integration for Muster."

// The Spring Boot to build and test against; -PspringBootVersion tests another one, and
// -PspringBootVersion=latest the newest that the catalog names. What is published always names the
// oldest supported version, and no platform: an application's own Spring Boot manages the
// versions.
val springBootVersion =
  providers
    .gradleProperty("springBootVersion")
    .map { if (it == "latest") libs.versions.spring.boot.latest.get() else it }
    .getOrElse(libs.versions.spring.boot.asProvider().get())
val springBoot = "org.springframework.boot:spring-boot-dependencies:$springBootVersion"

dependencies {
  api(project(":muster-core"))
  api(libs.spring.boot.autoconfigure)
  compileOnly(platform(springBoot))
  // Optional: the caller comes from Spring Security when the application has it.
  compileOnly("org.springframework.security:spring-security-core")
  // Optional: rules run inside the transaction when the application has Spring's transactions.
  compileOnly("org.springframework:spring-tx")
  // Optional: violations become problem details in a Spring MVC application.
  compileOnly("org.springframework:spring-webmvc")
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      dependencies {
        implementation(platform(springBoot))
        implementation("org.springframework.boot:spring-boot-starter-test")
        implementation("org.springframework.security:spring-security-core")
        implementation("org.springframework:spring-tx")
        implementation("org.springframework.boot:spring-boot-starter-webmvc")
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
