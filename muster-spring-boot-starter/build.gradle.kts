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

dependencies {
  api(project(":muster-core"))
  api(platform(libs.spring.boot.dependencies))
  api("org.springframework.boot:spring-boot-autoconfigure")
  // Optional: the caller comes from Spring Security when the application has it.
  compileOnly("org.springframework.security:spring-security-core")
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      dependencies {
        implementation(platform(libs.spring.boot.dependencies))
        implementation("org.springframework.boot:spring-boot-starter-test")
        implementation("org.springframework.security:spring-security-core")
      }
    }
    register<JvmTestSuite>("integrationTest") {
      dependencies {
        implementation(project())
        implementation(platform(libs.spring.boot.dependencies))
        implementation("org.spockframework:spock-spring")
        implementation("org.springframework.boot:spring-boot-starter-jdbc")
        implementation("org.springframework.boot:spring-boot-starter-test")
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
