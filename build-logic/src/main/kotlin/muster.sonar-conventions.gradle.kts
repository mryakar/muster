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
// Sends the analysis of the build to SonarQube Cloud, for the root project: the code, the tests and
// the coverage of every module that is published. CI runs it where the token is available.
import org.sonarqube.gradle.SonarExtension

plugins { id("org.sonarqube") }

sonar {
  properties {
    property("sonar.host.url", "https://sonarcloud.io")
    property("sonar.organization", "mryakar")
    property("sonar.projectKey", "mryakar_muster")
    // The scanner runs on the build's JDK rather than on a JRE it would download, which the
    // dependency verification of the build does not cover.
    property("sonar.scanner.skipJreProvisioning", "true")
  }
}

// The benchmarks are not published, and their generated JMH code is not Muster's.
project(":muster-benchmarks") { extensions.configure<SonarExtension> { isSkipProject = true } }
