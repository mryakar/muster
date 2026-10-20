plugins {
  `java-library`
  groovy
  jacoco
  id("com.diffplug.spotless")
  id("info.solidsoft.pitest")
}

val libs = versionCatalogs.named("libs")

fun version(alias: String): String = libs.findVersion(alias).get().requiredVersion

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

tasks.withType<JavaCompile>().configureEach { options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror")) }

dependencies {
  compileOnly(libs.findLibrary("jspecify").get())
  testImplementation(platform(libs.findLibrary("groovy-bom").get()))
  testImplementation(platform(libs.findLibrary("junit-bom").get()))
  testImplementation(platform(libs.findLibrary("spock-bom").get()))
}

testing {
  suites.withType<JvmTestSuite>().configureEach {
    useSpock(version("spock"))
    dependencies { runtimeOnly("org.junit.platform:junit-platform-launcher") }
  }
}

// Code is compiled for Java 21; -PtestJavaVersion runs the tests on a newer JVM.
val testJavaVersion = providers.gradleProperty("testJavaVersion").map(String::toInt).orElse(21)

tasks.withType<Test>().configureEach {
  javaLauncher = javaToolchains.launcherFor { languageVersion = testJavaVersion.map(JavaLanguageVersion::of) }
}

jacoco { toolVersion = version("jacoco") }

// Coverage counts every test suite, integration tests included.
val allExecutionData = fileTree(layout.buildDirectory.dir("jacoco")) { include("*.exec") }

tasks.jacocoTestReport {
  dependsOn(tasks.withType<Test>())
  executionData.setFrom(allExecutionData)
  reports { xml.required = true }
}

tasks.jacocoTestCoverageVerification {
  dependsOn(tasks.withType<Test>())
  executionData.setFrom(allExecutionData)
  violationRules {
    rule {
      listOf("INSTRUCTION", "BRANCH", "LINE").forEach {
        limit {
          counter = it
          minimum = "1.0".toBigDecimal()
        }
      }
    }
  }
}

tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }

pitest {
  pitestVersion = version("pitest")
  junit5PluginVersion = version("pitest-junit5-plugin")
  targetClasses = setOf("me.yakar.muster.*")
  threads = Runtime.getRuntime().availableProcessors()
  outputFormats = setOf("HTML", "XML")
  timestampedReports = false
  mutationThreshold = 95
  failWhenNoMutations = false
}

val licenseHeader = rootProject.file("config/spotless/license-header.txt")

spotless {
  java {
    target("src/*/java/**/*.java")
    googleJavaFormat(version("google-java-format"))
    licenseHeaderFile(licenseHeader, "(package|import|module|open|@)")
  }
  groovy {
    target("src/*/groovy/**/*.groovy")
    greclipse().configFile(rootProject.file("config/spotless/greclipse.properties"))
    licenseHeaderFile(licenseHeader, "(package|import)")
  }
}
