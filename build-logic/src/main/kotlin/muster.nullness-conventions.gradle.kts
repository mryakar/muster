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
// Checks the nullness of the main code with NullAway, against its JSpecify annotations: a value
// that may be null never reaches a place that does not allow it. Error Prone runs NullAway in the
// compiler; its own checks are off.
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
  java
  id("net.ltgt.errorprone")
}

val libs = versionCatalogs.named("libs")

dependencies {
  errorprone(libs.findLibrary("error-prone-core").get())
  errorprone(libs.findLibrary("nullaway").get())
  // Newer than Error Prone takes: the version it would take has a known vulnerability.
  constraints { errorprone(libs.findLibrary("guava").get()) }
}

tasks.withType<JavaCompile>().configureEach {
  options.errorprone {
    // The main code only: the tests and other compilations keep javac as it is.
    enabled = name == "compileJava"
    disableAllChecks = true
    check("NullAway", CheckSeverity.ERROR)
    option("NullAway:OnlyNullMarked", "true")
    option("NullAway:JSpecifyMode", "true")
  }
}
