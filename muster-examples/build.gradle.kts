plugins { id("muster.java-conventions") }

description = "Compiled examples that the documentation and integration tests build on."

dependencies { implementation(project(":muster-spring-boot-starter")) }
