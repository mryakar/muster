plugins { id("muster.java-conventions") }

description = "Spring Boot integration for Muster."

dependencies {
  api(project(":muster-core"))
  api(platform(libs.spring.boot.dependencies))
  api("org.springframework.boot:spring-boot-autoconfigure")
}

tasks.jar { manifest { attributes("Automatic-Module-Name" to "me.yakar.muster.spring") } }
