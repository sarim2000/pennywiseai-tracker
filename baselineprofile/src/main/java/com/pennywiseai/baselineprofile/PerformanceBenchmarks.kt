package com.pennywiseai.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Library-only versus library plus ours is two APKs, not two modes: see docs/performance.md.
 * Require installs every rule in the APK's baseline.prof. Disable installs none, not even
 * the libraries', and needs warmup iterations (its constructor throws without them), so it
 * shows what a JIT-only install reaches after use.
 */
@RunWith(AndroidJUnit4::class)
class PerformanceBenchmarks {
    @get:Rule val rule = MacrobenchmarkRule()

    @Before fun seed() = seedApp()

    @Test fun startupProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test fun startupWarmupOnly() = startup(WARMUP_ONLY)

    @Test fun scrollProfile() = scroll(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test fun scrollWarmupOnly() = scroll(WARMUP_ONLY)

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        waitForHome()
    }

    private fun scroll(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        iterations = ITERATIONS,
        // No startupMode: COLD would kill the app after this block, undoing the navigation.
        setupBlock = {
            killProcess()
            pressHome()
            startActivityAndWait()
            waitForHome()
            openTransactions()
        },
    ) {
        scrollTransactions()
    }

    private companion object {
        const val ITERATIONS = 10
        val WARMUP_ONLY = CompilationMode.Partial(BaselineProfileMode.Disable, warmupIterations = 3)
    }
}
