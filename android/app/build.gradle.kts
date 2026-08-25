plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.hr.app"
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.hr.app"
        // API 26 covers >97% of active devices and is the floor for a Keystore key that can
        // require user authentication — the mechanism the whole biometric login design rests on.
        // See docs/03-architecture.md §5.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/\"")
            // Certificate pinning is disabled in debug so a proxy can be attached while
            // developing. It is enforced in release — see NetworkModule.
            buildConfigField("boolean", "CERTIFICATE_PINNING", "false")
        }
        // Release-shaped but signed with the debug key, so Macrobenchmark can
        // install and drive it. Measuring a debug build would report numbers
        // three to five times worse than any user sees.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            // Lets the profiler attach without making the build debuggable.
            isProfileable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", "\"https://api.hrapp.io/\"")
            buildConfigField("boolean", "CERTIFICATE_PINNING", "true")
        }
    }

    // Per-ABI splits keep each delivered artefact well under the 25 MB budget in
    // docs/05-screens-ux.md §8. The incumbent ships a 92.5 MB universal APK.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
            "/META-INF/LICENSE*",
        )
    }

    sourceSets {
        getByName("main") {
            // The API client is generated from spec/openapi.yaml — see clients/README.md.
            // Consumed as source rather than as a nested Gradle module; a wrapper inside a
            // wrapper buys nothing.
            kotlin.srcDir("../../clients/kotlin/src/main/kotlin")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher)
    implementation(libs.sqlite.ktx)
    implementation(libs.datastore.preferences)

    implementation(libs.work.runtime.ktx)

    implementation(libs.retrofit)
    implementation(libs.retrofit.scalars)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.work.testing)
}

/**
 * APK size budgets, enforced as a build failure.
 *
 * ## Why this is a gate rather than a report
 *
 * `docs/05-screens-ux.md` §8 sets an Android budget of 25 MB, against the
 * incumbent's 92.5 MB. A budget nobody enforces is an aspiration: size creeps
 * a megabyte at a time, every increment is individually defensible, and by the
 * time anyone measures it the causes are spread across six months of commits.
 *
 * Failing the build at the moment the budget is exceeded makes the cause
 * exactly one pull request wide.
 *
 * ## Debug builds are exempt
 *
 * They carry Compose tooling and no minification, so they are roughly four
 * times the size of a release build and measuring them would be meaningless.
 *
 *   ./gradlew :app:checkReleaseApkSize
 */

// Per-ABI budget. The app ships per-ABI splits, so this is what a user
// actually downloads — not the sum of all three.
val apkSizeBudgetBytes = 25L * 1024 * 1024

// Warn well before the failure threshold, so the conversation happens while
// there is still room rather than when the build is already red.
val apkSizeWarnBytes = (apkSizeBudgetBytes * 0.8).toLong()

fun formatMb(bytes: Long): String = String.format("%.1f MB", bytes / 1024.0 / 1024.0)

tasks.register("checkReleaseApkSize") {
    group = "verification"
    description = "Fails if any release APK exceeds the per-ABI size budget"

    val apkDirectory = layout.buildDirectory.dir("outputs/apk/release")
    val budget = apkSizeBudgetBytes
    val warnAt = apkSizeWarnBytes

    // Declared so Gradle can skip the task when nothing changed, and so the
    // configuration cache does not capture the project object.
    inputs.dir(apkDirectory).optional(true)

    doLast {
        val directory = apkDirectory.get().asFile
        if (!directory.exists()) {
            throw GradleException(
                "No release APKs found in ${directory.path}.\n" +
                    "Run :app:assembleRelease first.",
            )
        }

        val apks = directory.listFiles { file -> file.extension == "apk" }?.sortedBy { it.name }.orEmpty()
        if (apks.isEmpty()) {
            throw GradleException("No release APKs found in ${directory.path}. Run :app:assembleRelease first.")
        }

        val oversized = mutableListOf<String>()

        logger.lifecycle("APK size budget: ${formatMb(budget)} per ABI")
        apks.forEach { apk ->
            val size = apk.length()
            val status =
                when {
                    size > budget -> "FAIL"
                    size > warnAt -> "WARN"
                    else -> "ok"
                }
            logger.lifecycle("  %-42s %9s  %s".format(apk.name, formatMb(size), status))

            if (size > budget) {
                oversized += "${apk.name} is ${formatMb(size)}, over the ${formatMb(budget)} budget"
            } else if (size > warnAt) {
                logger.warn(
                    "  ${apk.name} is at ${(size * 100 / budget)}% of budget — worth looking at before it fails.",
                )
            }
        }

        if (oversized.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("APK size budget exceeded:")
                    oversized.forEach { appendLine("  - $it") }
                    appendLine()
                    appendLine("The budget is in docs/05-screens-ux.md §8 and is a competitive")
                    appendLine("differentiator, not an arbitrary limit. Before raising it:")
                    appendLine("  1. ./gradlew :app:assembleRelease --scan  and inspect the APK")
                    appendLine("  2. Check for a dependency pulling in resources or native libs")
                    appendLine("  3. Confirm R8 is still shrinking (isMinifyEnabled)")
                },
            )
        }
    }
}

// `tasks.matching { … }.configureEach` rather than `tasks.named(…)`: the
// task is registered during configuration, before AGP has created its variant
// tasks, so `assembleRelease` does not exist yet and `named` would fail
// outright. Matching is lazy and picks the task up whenever AGP creates it.
tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy("checkReleaseApkSize")
}
