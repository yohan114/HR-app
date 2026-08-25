plugins {
    alias(libs.plugins.android.application) apply false
    // Declared here so its version is resolved once for the whole build. A
    // module applying it with a version while another Android plugin is
    // already on the classpath fails with "compatibility cannot be checked".
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
