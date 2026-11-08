package com.pennywiseai.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Before fun seed() = seedApp()

    // Only this journey feeds the startup profile (DEX layout); it must stay startup-only.
    @Test fun startup() = rule.collect(PACKAGE, includeInStartupProfile = true, filterPredicate = ::notSeedCode) {
        pressHome()
        startActivityAndWait()
        waitForHome()
    }

    @Test fun transactions() = rule.collect(PACKAGE, filterPredicate = ::notSeedCode) {
        pressHome()
        startActivityAndWait()
        waitForHome()
        openTransactions()
        scrollTransactions()
    }
}

// Hilt loads the seed receiver's injector with the app component; it never ships in release.
private fun notSeedCode(rule: String) = "BenchmarkSeed" !in rule
