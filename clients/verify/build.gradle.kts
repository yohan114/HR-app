import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Compilation check for the generated Kotlin client.
 *
 * The Android app does not exist yet, so nothing else would notice if a spec change produced a
 * client that does not compile — we would find out in Phase 1, well after the change that caused
 * it. This project exists purely to close that gap: it pulls the generated sources in and builds
 * them against the same dependencies the Android module will use.
 *
 * It produces no artefact anyone consumes. When `android/` lands in Phase 1, that module takes
 * over the role and this can be deleted.
 */
plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
    sourceSets["main"].kotlin.srcDir("../kotlin/src/main/kotlin")
}

// This project's sources are produced by a task in another project. Without an explicit
// dependency Gradle is free to compile before generation finishes — which it did, intermittently,
// on the first run that combined both tasks.
tasks.named("compileKotlin") {
    dependsOn(":generateKotlinClient")
}

// Compiling proves the shape is valid; it proves nothing about whether
// kotlinx.serialization can resolve a serialiser for every field at runtime.
// The generator emits `@Contextual` on free-form JSON, which defers resolution
// to a SerializersModule — so a missing registration is a device-side crash
// that no build stage would see.
tasks.named<Test>("test") {
    useJUnitPlatform()
    dependsOn(":generateKotlinClient")
    testLogging { events("passed", "failed") }
}

dependencies {
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")

    testImplementation(kotlin("test"))
}
