plugins { id("muster.java-conventions") }

description = "Business rules checked before your service method runs."

// The API docs are part of the product; a broken link or a missing comment fails the build.
tasks.check { dependsOn(tasks.javadoc) }
