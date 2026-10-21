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

// Internal packages are not exported, so the API docs leave them out. The source path lets the
// documented classes still refer to them.
tasks.withType<Javadoc>().configureEach {
  exclude("**/internal/**")
  (options as StandardJavadocDocletOptions).apply {
    addStringOption("-source-path", sourceSets["main"].java.srcDirs.joinToString(File.pathSeparator))
    addBooleanOption("Xdoclint:all", true)
    addBooleanOption("Xwerror", true)
  }
}

// Published to consumers: modules that require me.yakar.muster read org.jspecify, and null-aware tools
// need the annotations on their classpath.
dependencies { api(libs.findLibrary("jspecify").get()) }

testing {
  suites.withType<JvmTestSuite>().configureEach {
    useSpock(version("spock"))
    dependencies {
      implementation(platform(libs.findLibrary("groovy-bom").get()))
      implementation(platform(libs.findLibrary("junit-bom").get()))
      implementation(platform(libs.findLibrary("spock-bom").get()))
      runtimeOnly("org.junit.platform:junit-platform-launcher")
    }
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
