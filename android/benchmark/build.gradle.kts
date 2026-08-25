plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

/**
 * Macrobenchmark module.
 *
 * Measures the metrics the app is judged on in docs/05-screens-ux.md §8 —
 * cold start under 1.2s, warm start under 400ms — against a real device or
 * emulator. A separate module because Macrobenchmark drives the app from
 * outside its own process, which is the only way to measure startup honestly:
 * an in-process test cannot observe the time before its own code runs.
 *
 *   ./gradlew :benchmark:connectedBenchmarkAndroidTest
 */
android {
    namespace = "com.hr.benchmark"
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    defaultConfig {
        // Macrobenchmark needs API 24+ to read the frame and startup metrics
        // it depends on, regardless of the app's own minSdk of 26.
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildTypes {
        // Benchmarks run against a release-shaped build: R8-minified, not
        // debuggable. Measuring a debug build would report numbers three to
        // five times worse than anything a user experiences, which is worse
        // than not measuring at all — it produces alarm about a problem that
        // does not exist and hides the ones that do.
        create("benchmark") {
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.espresso.core)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
    beforeVariants(selector().all()) { variant ->
        // Only the benchmark variant is useful; building the others wastes CI
        // minutes and produces artefacts nobody consumes.
        variant.enable = variant.buildType == "benchmark"
    }
}
