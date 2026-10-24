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
// Uses a published Muster the way an application would. It is a build of its own, so that it sees
// only what Muster publishes: run it after publishing to the local repository.
rootProject.name = "muster-consumer-test"

// -PpomOnly reads only the POMs, as Maven users do, instead of the Gradle module metadata.
val pomOnly = providers.gradleProperty("pomOnly").isPresent

dependencyResolutionManagement {
  repositories {
    mavenLocal {
      content { includeGroup("me.yakar.muster") }
      if (pomOnly) {
        metadataSources {
          mavenPom()
          artifact()
          ignoreGradleMetadataRedirection()
        }
      }
    }
    mavenCentral { content { excludeGroup("me.yakar.muster") } }
  }
  // The Spring Boot versions that Muster is built against.
  versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}

// An application without Spring, on the module path, and a Spring Boot application.
include("spring")
