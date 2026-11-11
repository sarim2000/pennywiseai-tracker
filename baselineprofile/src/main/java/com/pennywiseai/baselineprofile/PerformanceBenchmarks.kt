package com.pennywiseai.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.MemoryUsageMetric
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
@OptIn(ExperimentalMetricApi::class)
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
        // Memory as Home settles: what the app costs just for being open.
        metrics = listOf(StartupTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Last)),
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
        // Peak memory while every seeded row passes through the list.
        metrics = listOf(FrameTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Max)),
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
