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
import org.cyclonedx.model.ExternalReference

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
      // A random serial number would make every build's SBOM differ.
      includeBomSerialNumber = false
      // The repository's public address, not whatever remote the build machine's clone uses.
      externalReferences =
        listOf(
          ExternalReference().apply {
            type = ExternalReference.Type.VCS
            url = "https://github.com/mryakar/muster"
          },
        )
      // The plugin adds a project_path qualifier, the module's path in this build, to the purls of
      // the project's own modules; the published artifacts have no such qualifier.
      val boms = listOf(jsonOutput, xmlOutput)
      doLast {
        boms.forEach { bom ->
          val file = bom.get().asFile
          if (file.exists()) {
            val text =
              file
                .readText()
                .replace(
                  Regex("""(pkg:maven/me\.yakar\.muster/[^"<?]*)\?project_path=[^"&<]*&(amp;)?"""),
                  "$1?",
                )
                .replace(
                  Regex("""(pkg:maven/me\.yakar\.muster/[^"<?]*)\?project_path=[^"&<]*"""),
                  "$1",
                )
            val purl = Regex("""pkg:[^"<]*[?&](amp;)?project_path=""").find(text)
            if (purl != null) {
              throw GradleException("$file names the path of a module in this build: ${purl.value}")
            }
            file.writeText(text)
          }
        }
      }
    }
  publishing.publications.withType<MavenPublication>().configureEach {
    artifact(sbom.flatMap { it.jsonOutput }) {
      classifier = "cyclonedx"
      extension = "json"
    }
  }
}
