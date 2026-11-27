package com.pennywiseai.tracker.core.entitlement

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureTrialsTest {

    private val stored = mutableMapOf<String, Any>()
    private var clock = START
    private val trials = testFeatureTrials(stored) { clock }

    @Test
    fun `nothing is started until the user starts it`() {
        assertEquals(TrialStatus.NotStarted, trials.status(TrialFeature.ANALYTICS_GROUPING))
        assertFalse(trials.isActive(TrialFeature.ANALYTICS_GROUPING))
    }

    @Test
    fun `a fresh trial has seven days left`() {
        trials.start(TrialFeature.CSV_EXPORT, isPro = false)
        assertEquals(TrialStatus.Active(7), trials.status(TrialFeature.CSV_EXPORT))
    }

    @Test
    fun `starting again never restarts the trial`() {
        trials.start(TrialFeature.CSV_EXPORT, isPro = false)
        clock += days(3)
        trials.start(TrialFeature.CSV_EXPORT, isPro = false)
        assertEquals(START, stored[TrialFeature.CSV_EXPORT.name])
        assertEquals(TrialStatus.Active(4), trials.status(TrialFeature.CSV_EXPORT))

        clock += days(5)
        trials.start(TrialFeature.CSV_EXPORT, isPro = false)
        assertEquals(TrialStatus.Expired, trials.status(TrialFeature.CSV_EXPORT))
    }

    @Test
    fun `the trial ends exactly seven days after it starts`() {
        trials.start(TrialFeature.ANALYTICS_GROUPING, isPro = false)
        clock = START + days(7) - 1
        assertEquals(TrialStatus.Active(1), trials.status(TrialFeature.ANALYTICS_GROUPING))
        clock = START + days(7)
        assertEquals(TrialStatus.Expired, trials.status(TrialFeature.ANALYTICS_GROUPING))
    }

    @Test
    fun `a clock set before the start reads as expired`() {
        trials.start(TrialFeature.ANALYTICS_GROUPING, isPro = false)
        clock = START - 1
        assertEquals(TrialStatus.Expired, trials.status(TrialFeature.ANALYTICS_GROUPING))
    }

    @Test
    fun `an ended trial stays ended when the clock goes back into it`() {
        trials.start(TrialFeature.ANALYTICS_GROUPING, isPro = false)
        clock = START + days(8)
        assertEquals(TrialStatus.Expired, trials.status(TrialFeature.ANALYTICS_GROUPING))

        clock = START + days(2)
        assertEquals(TrialStatus.Expired, trials.status(TrialFeature.ANALYTICS_GROUPING))
    }

    @Test
    fun `a clock behind the latest time seen reads as expired until it catches up`() {
        trials.start(TrialFeature.CSV_EXPORT, isPro = false)
        clock = START + days(3)
        assertEquals(TrialStatus.Active(4), trials.status(TrialFeature.CSV_EXPORT))

        clock = START + days(1)
        assertEquals(TrialStatus.Expired, trials.status(TrialFeature.CSV_EXPORT))

        clock = START + days(4)
        assertEquals(TrialStatus.Active(3), trials.status(TrialFeature.CSV_EXPORT))
    }

    @Test
    fun `a clock correction on one trial doesn't spend or delay another`() {
        trials.start(TrialFeature.ANALYTICS_GROUPING, isPro = false)
        clock = START + days(3)
        trials.status(TrialFeature.ANALYTICS_GROUPING)

        clock = START + days(1)
        trials.start(TrialFeature.CSV_EXPORT, isPro = false)
        assertEquals(START + days(1), stored[TrialFeature.CSV_EXPORT.name])
        assertEquals(TrialStatus.Active(7), trials.status(TrialFeature.CSV_EXPORT))
        assertEquals(TrialStatus.Expired, trials.status(TrialFeature.ANALYTICS_GROUPING))

        clock = START + days(3)
        assertEquals(TrialStatus.Active(5), trials.status(TrialFeature.CSV_EXPORT))
    }

    @Test
    fun `each feature has its own trial`() {
        trials.start(TrialFeature.ANALYTICS_GROUPING, isPro = false)
        clock += days(8)
        assertEquals(TrialStatus.Expired, trials.status(TrialFeature.ANALYTICS_GROUPING))
        assertEquals(TrialStatus.NotStarted, trials.status(TrialFeature.CSV_EXPORT))

        trials.start(TrialFeature.CSV_EXPORT, isPro = false)
        assertEquals(TrialStatus.Active(7), trials.status(TrialFeature.CSV_EXPORT))
        assertEquals(TrialStatus.Expired, trials.status(TrialFeature.ANALYTICS_GROUPING))
    }

    @Test
    fun `access re-reads when a trial starts`() = runBlocking {
        val isPro = MutableStateFlow(false)
        val access = trials.access(TrialFeature.CSV_EXPORT, isPro)
        val before = access.first()
        assertFalse(before.unlocked)
        assertTrue(before.canStartTrial)

        trials.start(TrialFeature.CSV_EXPORT, isPro = false)
        val during = access.first()
        assertTrue(during.unlocked)
        assertFalse(during.canStartTrial)
        assertEquals(7, during.trialDaysLeft)
    }

    @Test
    fun `an open gate counts down and locks the moment its trial ends`() = runTest {
        val trials = testFeatureTrials { START + testScheduler.currentTime }
        trials.start(TrialFeature.ANALYTICS_GROUPING, isPro = false)
        val seen = mutableListOf<FeatureAccess>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            trials.access(TrialFeature.ANALYTICS_GROUPING, MutableStateFlow(false)).collect { seen += it }
        }
        assertEquals(7, seen.last().trialDaysLeft)

        advanceTimeBy(days(6) + 1)
        assertEquals(1, seen.last().trialDaysLeft)
        assertTrue(seen.last().unlocked)

        advanceTimeBy(days(1))
        assertEquals(TrialStatus.Expired, seen.last().trial)
        assertFalse(seen.last().unlocked)
        assertFalse(seen.last().canStartTrial)
    }

    @Test
    fun `a recheck re-reads an open gate after the clock moved on`() = runTest {
        trials.start(TrialFeature.CSV_EXPORT, isPro = false)
        val seen = mutableListOf<FeatureAccess>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            trials.access(TrialFeature.CSV_EXPORT, MutableStateFlow(false)).collect { seen += it }
        }
        assertTrue(seen.last().unlocked)

        // The device slept through the expiry, so the timed re-check hasn't fired.
        clock += days(8)
        trials.recheck()
        assertFalse(seen.last().unlocked)
    }

    @Test
    fun `no trial starts or is offered until the user is known to be free`() {
        trials.start(TrialFeature.CSV_EXPORT, isPro = null)
        trials.start(TrialFeature.CSV_EXPORT, isPro = true)
        assertEquals(TrialStatus.NotStarted, trials.status(TrialFeature.CSV_EXPORT))

        val loading = FeatureAccess(isPro = null, trial = TrialStatus.NotStarted)
        assertFalse(loading.unlocked)
        assertFalse(loading.canStartTrial)
    }

    @Test
    fun `Pro wins over any trial state and shows no trial`() {
        listOf(TrialStatus.NotStarted, TrialStatus.Active(3), TrialStatus.Expired).forEach { trial ->
            val access = FeatureAccess(isPro = true, trial = trial)
            assertTrue(access.unlocked)
            assertFalse(access.canStartTrial)
            assertNull(access.trialDaysLeft)
        }
    }

    @Test
    fun `an expired trial locks again and offers no second trial`() {
        val access = FeatureAccess(isPro = false, trial = TrialStatus.Expired)
        assertFalse(access.unlocked)
        assertFalse(access.canStartTrial)
        assertNull(access.trialDaysLeft)
    }

    private companion object {
        val START = TimeUnit.DAYS.toMillis(20_000)

        fun days(n: Int) = TimeUnit.DAYS.toMillis(n.toLong())
    }
}

/** [FeatureTrials] over an in-memory [stored] prefs map, read at [now]. */
internal fun testFeatureTrials(
    stored: MutableMap<String, Any> = mutableMapOf(),
    now: () -> Long = System::currentTimeMillis,
): FeatureTrials {
    val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    every { editor.putLong(any(), any()) } answers { stored[firstArg()] = secondArg<Long>(); editor }
    every { editor.putBoolean(any(), any()) } answers { stored[firstArg()] = secondArg<Boolean>(); editor }
    val prefs = mockk<SharedPreferences> {
        every { getLong(any(), any()) } answers { stored[firstArg()] as Long? ?: secondArg() }
        every { getBoolean(any(), any()) } answers { stored[firstArg()] as Boolean? ?: secondArg() }
        every { contains(any()) } answers { firstArg<String>() in stored }
        every { edit() } returns editor
    }
    return FeatureTrials(prefs, now)
}
