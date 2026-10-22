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
// Publishes a module to Maven Central, with sources, API docs and a signed POM, and a library with
// its software bill of materials.
import org.cyclonedx.gradle.CyclonedxDirectTask
import org.cyclonedx.model.Component

plugins { id("com.vanniktech.maven.publish") }

mavenPublishing {
  // Uploads only: a release becomes public when it is published in the Central Portal.
  publishToMavenCentral(automaticRelease = false)

  // CI provides the key; without it, publishing to the local repository works unsigned.
  if (providers.gradleProperty("signingInMemoryKey").isPresent) {
    signAllPublications()
  }

  pom {
    name = project.name
    description = provider { project.description }
    url = "https://github.com/mryakar/muster"
    inceptionYear = "2026"
    licenses {
      license {
        name = "Apache-2.0"
        url = "https://www.apache.org/licenses/LICENSE-2.0"
        distribution = "repo"
      }
    }
    developers {
      developer {
        id = "mryakar"
        name = "Amo"
        url = "https://github.com/mryakar"
      }
    }
    scm {
      url = "https://github.com/mryakar/muster"
      connection = "scm:git:https://github.com/mryakar/muster.git"
      developerConnection = "scm:git:ssh://git@github.com/mryakar/muster.git"
    }
    issueManagement {
      system = "GitHub"
      url = "https://github.com/mryakar/muster/issues"
    }
  }
}

// The SBOM lists what a library brings to its users at run time, nothing the build uses.
pluginManager.withPlugin("java-library") {
  apply(plugin = "org.cyclonedx.bom")
  val sbom =
    tasks.named<CyclonedxDirectTask>("cyclonedxDirectBom") {
      includeConfigs = listOf("runtimeClasspath")
      projectType = Component.Type.LIBRARY
    }
  publishing.publications.withType<MavenPublication>().configureEach {
    artifact(sbom.flatMap { it.jsonOutput }) {
      classifier = "cyclonedx"
      extension = "json"
    }
  }
}
