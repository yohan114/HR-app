plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.openapi.generator)
}

import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

group = "com.hr"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        // Java 21 for virtual threads (see docs/adr/0002-backend-stack.md).
        // Gradle auto-provisions the JDK if it is not installed locally.
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.modulith.bom.get().toString())
        mavenBom(libs.testcontainers.bom.get().toString())
    }
}

dependencies {
    // Web / API
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    runtimeOnly(libs.micrometer.registry.prometheus)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)

    // Security
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.bouncycastle)

    // Persistence
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    // Cache / locks
    implementation(libs.spring.boot.starter.data.redis)

    // Modulith
    implementation(libs.spring.modulith.starter.core)
    implementation(libs.spring.modulith.starter.jpa)
    implementation(libs.spring.modulith.actuator)

    // Test
    testImplementation(libs.spring.boot.starter.test) {
        exclude(module = "mockito-core")
    }
    testImplementation(libs.spring.security.test)
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.spring.modulith.docs)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.mockk)
    testImplementation(libs.springmockk)
    // Parses spec/openapi.yaml in ApiContractTest, which asserts the implementation and the
    // published contract have not drifted apart.
    testImplementation(libs.swagger.parser)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }

    // ApiContractTest and MetricsContractTest read files outside this project —
    // the OpenAPI spec, the alert rules and the Grafana dashboards. Gradle has
    // no way to know that, so without these declarations it treats the test
    // task as up-to-date when only those files have changed, and a contract
    // regression sails through CI unnoticed.
    //
    // Found the hard way: renaming a metric in an alert rule did not re-run the
    // test that exists to catch exactly that.
    inputs.file(layout.projectDirectory.file("../spec/openapi.yaml"))
        .withPropertyName("openApiSpec")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    inputs.dir(layout.projectDirectory.dir("../infra/k8s/observability"))
        .withPropertyName("observabilityConfig")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

// ---------------------------------------------------------------------------
// Client generation from the OpenAPI contract (P0-BE-25 … P0-BE-28)
//
// spec/openapi.yaml is the single source of truth: the Kotlin, Swift and TypeScript clients are
// all generated from it, so a contract change propagates to every consumer mechanically rather
// than by three people remembering to make the same edit.
//
// Why these tasks live in the backend build: purely pragmatic — the backend already has the
// Gradle wrapper, and android/, ios/ and web/ do not exist yet. This moves to a root
// multi-project build when those land in Phase 1. Nothing in src/main references any generated
// code; this is build tooling, not a runtime dependency.
//
// Generated output is committed. Deliberately: the iOS and web projects do not run Gradle, so
// build-time-only generation would force a JVM toolchain on those teams. Committing it also makes
// contract changes visible in review — a PR that edits the spec shows exactly what it does to
// every client. CI regenerates and fails if the committed output is stale.
// ---------------------------------------------------------------------------

val openApiSpec = layout.projectDirectory.file("../spec/openapi.yaml").asFile
val clientsDir = layout.projectDirectory.dir("../clients").asFile

fun GenerateTask.commonClientConfig() {
    group = "openapi"
    // A file URI rather than an absolute path: on Windows the generator parses the value as a
    // URI, and `D:\HR\...` fails because the backslash is illegal in the opaque part.
    inputSpec.set(openApiSpec.toURI().toString())
    validateSpec.set(true)
    cleanupOutput.set(true)
    // Note the generator's convention here: naming a category with an empty value means "generate
    // ALL of these", not "generate none". Supporting files are therefore requested deliberately —
    // they carry the runtime (ApiClient, serializers, URLSession plumbing) without which the
    // generated APIs do not function. The build scaffolding that comes with them is removed by
    // `stripScaffolding` below.
    globalProperties.set(
        mapOf(
            "models" to "",
            "apis" to "",
            "supportingFiles" to "",
            "modelDocs" to "false",
            "apiDocs" to "false",
            "modelTests" to "false",
            "apiTests" to "false",
        ),
    )
}

/**
 * Removes the standalone-project scaffolding the generator emits alongside the client runtime.
 *
 * The generators assume their output is a self-contained library with its own build, wrapper and
 * packaging manifests. Ours is not — it is consumed as source by projects that already have all
 * of that. A nested Gradle wrapper inside the repository is actively confusing, and a CocoaPods
 * podspec for a package we distribute via SPM is dead weight.
 */
fun GenerateTask.stripScaffolding(vararg relativePaths: String) {
    doLast {
        val root = File(outputDir.get())
        relativePaths.forEach { path ->
            val target = File(root, path)
            if (target.exists()) target.deleteRecursively()
        }
    }
}

/**
 * Makes the generated JSON encoder omit properties that were never set.
 *
 * The generator emits `Json { encodeDefaults = true; ... }` with no
 * `explicitNulls` setting, so every unset nullable property is serialised as an
 * explicit `null`. For a PATCH body that is fatal: a client changing one field
 * sends forty nulls alongside it, and the server — which cannot distinguish
 * "not mentioned" from "set to nothing" — either clears the rest of the record
 * or, in our case, rejects the request for trying to blank the required fields
 * and to write fields the caller has no permission for.
 *
 * `ClientSerialisationTest` is what found this, and is what fails if a
 * generator upgrade moves the line this patch matches. Patching generated
 * output is unpleasant, but the alternatives are worse: the kotlin generator
 * exposes no option for it, and hand-maintaining a fork of the runtime to
 * change one flag is a much larger thing to keep correct.
 *
 * Note the consequence: with nulls omitted, a typed client can no longer
 * express "clear this field" by sending null. That is what `clearFields` on the
 * request bodies is for.
 */
fun GenerateTask.omitNullsWhenEncoding() {
    doLast {
        val serializer = File(outputDir.get(), "src/main/kotlin/com/hr/client/infrastructure/Serializer.kt")
        require(serializer.exists()) { "Serializer.kt not found at ${serializer.path} — did the generator layout change?" }

        val original = serializer.readText()
        val marker = "encodeDefaults = true"
        require(original.contains(marker)) {
            "Serializer.kt no longer contains '$marker'. The generated Json configuration has changed; " +
                "re-check that unset properties are still omitted before removing this patch."
        }
        if (original.contains("explicitNulls")) return@doLast

        serializer.writeText(
            original.replace(
                marker,
                "$marker\n            // Patched by :generateKotlinClient — see omitNullsWhenEncoding().\n" +
                    "            // Without this, a PATCH body carries an explicit null for every unset field.\n" +
                    "            explicitNulls = false",
            ),
        )
    }
}

/**
 * Android client — Retrofit 2 + kotlinx.serialization + coroutines.
 *
 * Retrofit rather than Ktor: the Android app already uses OkHttp for certificate pinning and
 * interceptors, and carrying two HTTP stacks is not free against a 25 MB binary budget.
 */
tasks.register<GenerateTask>("generateKotlinClient") {
    description = "Generates the Android (Kotlin/Retrofit) client from the OpenAPI spec"
    commonClientConfig()
    generatorName.set("kotlin")
    outputDir.set(File(clientsDir, "kotlin").absolutePath)
    packageName.set("com.hr.client")
    apiPackage.set("com.hr.client.api")
    modelPackage.set("com.hr.client.model")
    configOptions.set(
        mapOf(
            "library" to "jvm-retrofit2",
            "serializationLibrary" to "kotlinx_serialization",
            "useCoroutines" to "true",
            "dateLibrary" to "java8",
            "enumPropertyNaming" to "UPPERCASE",
            "sourceFolder" to "src/main/kotlin",
        ),
    )
    // Free-form JSON (the `details` bag on ApiError) would otherwise become `Map<String, Any>`,
    // which kotlinx.serialization cannot serialise — the compiler plugin crashes outright rather
    // than reporting it. `JsonElement` is the native representation for arbitrary JSON and lets
    // callers destructure it properly.
    typeMappings.set(mapOf("AnyType" to "JsonElement"))
    importMappings.set(mapOf("JsonElement" to "kotlinx.serialization.json.JsonElement"))
    // The Android module consumes `src/main/kotlin` as an extra source directory, so it needs no
    // build of its own.
    stripScaffolding(
        "build.gradle", "settings.gradle", "gradlew", "gradlew.bat", "gradle",
        "README.md", "proguard-rules.pro", ".openapi-generator-ignore",
    )
    omitNullsWhenEncoding()
}

/**
 * iOS client — Swift with async/await over URLSession.
 *
 * `useSPMFileStructure` so the output is a Swift package the iOS project depends on directly,
 * rather than files someone has to drag into an Xcode target by hand.
 */
tasks.register<GenerateTask>("generateSwiftClient") {
    description = "Generates the iOS (Swift/URLSession) client from the OpenAPI spec"
    commonClientConfig()
    generatorName.set("swift5")
    outputDir.set(File(clientsDir, "swift").absolutePath)
    configOptions.set(
        mapOf(
            "projectName" to "HRClient",
            "responseAs" to "AsyncAwait",
            "useSPMFileStructure" to "true",
            "swiftPackagePath" to "Sources/HRClient",
        ),
    )
    // Carthage and CocoaPods manifests are dropped: we distribute via SPM only.
    stripScaffolding(
        "Cartfile", "HRClient.podspec", "git_push.sh", "project.yml",
        "README.md", ".swiftformat", ".gitignore", ".openapi-generator-ignore",
    )
    // The generator emits a manifest targeting iOS 11 with swift-tools-version 5.1. Our floor is
    // iOS 16 (see docs/03-architecture.md §1), and the generated async/await APIs cannot compile
    // against an iOS 11 deployment target anyway. Rather than patching the generated file with
    // regexes, we own it outright.
    doLast {
        File(File(outputDir.get()), "Package.swift").writeText(swiftPackageManifest)
    }
}

/**
 * The SPM manifest for the generated Swift client.
 *
 * `AnyCodable` is required by the generated models for the free-form `details` bag on `ApiError`.
 * Swift has no built-in "arbitrary JSON" type, so some dependency is unavoidable here; AnyCodable
 * is small, widely used and has no transitive dependencies of its own.
 */
val swiftPackageManifest =
    """
    // swift-tools-version:5.9
    // Generated by the `generateSwiftClient` Gradle task. Do not edit — see backend/build.gradle.kts.

    import PackageDescription

    let package = Package(
        name: "HRClient",
        platforms: [
            .iOS(.v16),
            .macOS(.v13),
        ],
        products: [
            .library(name: "HRClient", targets: ["HRClient"]),
        ],
        dependencies: [
            // Backs the free-form `details` object on ApiError. Swift has no native
            // arbitrary-JSON type.
            .package(url: "https://github.com/Flight-School/AnyCodable", .upToNextMajor(from: "0.6.7")),
        ],
        targets: [
            .target(
                name: "HRClient",
                dependencies: ["AnyCodable"],
                path: "Sources/HRClient"
            ),
        ]
    )
    """.trimIndent()

/**
 * Web admin client — TypeScript over the Fetch API.
 *
 * `typescript-fetch` rather than `typescript-axios`: the admin console targets evergreen browsers
 * only, so `fetch` is native and shipping an HTTP library would be dead weight.
 */
tasks.register<GenerateTask>("generateTypeScriptClient") {
    description = "Generates the web admin (TypeScript/fetch) client from the OpenAPI spec"
    commonClientConfig()
    generatorName.set("typescript-fetch")
    outputDir.set(File(clientsDir, "typescript").absolutePath)
    configOptions.set(
        mapOf(
            "supportsES6" to "true",
            "withInterfaces" to "true",
            "typescriptThreePlus" to "true",
            "useSingleRequestParameter" to "true",
        ),
    )
    stripScaffolding(".openapi-generator-ignore")
}

tasks.register("generateAllClients") {
    group = "openapi"
    description = "Regenerates every client from the OpenAPI spec"
    dependsOn("generateKotlinClient", "generateSwiftClient", "generateTypeScriptClient")
}
