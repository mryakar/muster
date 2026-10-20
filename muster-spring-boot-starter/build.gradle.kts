plugins { id("muster.java-conventions") }

description = "Spring Boot integration for Muster."

dependencies {
  api(project(":muster-core"))
  api(platform(libs.spring.boot.dependencies))
  api("org.springframework.boot:spring-boot-autoconfigure")
}

testing {
  suites {
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
