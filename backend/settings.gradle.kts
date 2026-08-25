rootProject.name = "hr-backend"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// Lets Gradle download the Java 21 toolchain automatically when it is not installed locally.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    // The client-verify project declares its own repositories, because it depends on the Android
    // HTTP stack rather than anything the backend uses.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
    }
}

// Compilation check for the generated Kotlin client. Included as a subproject so it shares the
// wrapper; it contributes nothing to the backend's own dependencies or artefacts.
// See clients/verify/build.gradle.kts.
include("client-verify")
project(":client-verify").projectDir = file("../clients/verify")
