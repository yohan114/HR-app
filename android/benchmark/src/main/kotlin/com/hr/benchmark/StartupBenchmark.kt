package com.hr.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startup timing.
 *
 * The budgets in docs/05-screens-ux.md §8 — cold start under 1.2s, warm start
 * under 400ms — are the measurable form of the product's central claim. The
 * incumbent's most common review complaint is that the app is *"very slow"*,
 * so these numbers are the differentiator rather than an engineering nicety.
 *
 * ## Why these are not assertions
 *
 * Macrobenchmark reports; it does not fail on a threshold. Asserting a wall
 * clock figure inside a test makes it flaky on shared CI hardware, where an
 * emulator on a busy runner can be twice as slow as the same emulator on an
 * idle one — and a flaky performance test gets muted, which is worse than
 * having none.
 *
 * The intended arrangement is to publish these to a tracking service and alert
 * on a *trend*, which distinguishes "this commit regressed startup" from "this
 * runner was busy". Until that exists, the results are uploaded as CI
 * artefacts and read by a human. That gap is recorded in PHASE-0-STATUS.md
 * rather than papered over with a threshold that would lie.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    /**
     * Cold start: process not running, nothing cached.
     *
     * The number that matters most — it is what a user experiences first thing
     * in the morning when they open the app to clock in.
     */
    @Test
    fun coldStartupNoCompilation() = measureStartup(StartupMode.COLD, CompilationMode.None())

    /**
     * Cold start with the baseline profile applied.
     *
     * This is what ships. A baseline profile ahead-of-time compiles the startup
     * path, and the difference against `CompilationMode.None` is the profile's
     * actual contribution — worth measuring both so a profile that has silently
     * stopped being generated is visible.
     */
    @Test
    fun coldStartupBaselineProfile() =
        measureStartup(
            StartupMode.COLD,
            CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
        )

    /** Warm start: process alive, activity recreated. Budget: 400ms. */
    @Test
    fun warmStartup() = measureStartup(StartupMode.WARM, CompilationMode.Partial())

    private fun measureStartup(
        startupMode: StartupMode,
        compilationMode: CompilationMode,
    ) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        // Ten iterations: enough for a stable median without making the CI job
        // long enough that people start skipping it.
        iterations = 10,
        startupMode = startupMode,
        compilationMode = compilationMode,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    private companion object {
        // The release build has no applicationIdSuffix; debug appends ".debug".
        // Benchmarks run against the benchmark build type, which inherits from
        // release.
        const val TARGET_PACKAGE = "com.hr.app"
    }
}
