plugins { `java-platform` }

description = "Bill of materials that aligns the versions of the Muster modules."

dependencies {
  constraints {
    api(project(":muster-core"))
    api(project(":muster-spring-boot-starter"))
  }
}
