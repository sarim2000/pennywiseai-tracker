package com.pennywiseai.tracker.core.entitlement

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class FeatureTrialsTest {

    private val stored = mutableMapOf<String, Long>()
    private var clock = START
    private val trials = testFeatureTrials(stored) { clock }

    @Test
    fun `nothing is started until the user starts it`() {
        assertEquals(TrialStatus.NotStarted, trials.status(TrialFeature.ANALYTICS_GROUPING))
        assertFalse(trials.isActive(TrialFeature.ANALYTICS_GROUPING))
    }

    @Test
    fun `a fresh trial has seven days left`() {
        trials.start(TrialFeature.CSV_EXPORT)
        assertEquals(TrialStatus.Active(7), trials.status(TrialFeature.CSV_EXPORT))
    }

    @Test
    fun `starting again never restarts the trial`() {
        trials.start(TrialFeature.CSV_EXPORT)
        clock += days(3)
        trials.start(TrialFeature.CSV_EXPORT)
        assertEquals(START, stored[TrialFeature.CSV_EXPORT.name])
        assertEquals(TrialStatus.Active(4), trials.status(TrialFeature.CSV_EXPORT))

        clock += days(5)
        trials.start(TrialFeature.CSV_EXPORT)
        assertEquals(TrialStatus.Expired, trials.status(TrialFeature.CSV_EXPORT))
    }

    @Test
    fun `the trial ends exactly seven days after it starts`() {
        trials.start(TrialFeature.ANALYTICS_GROUPING)
        clock = START + days(7) - 1
        assertEquals(TrialStatus.Active(1), trials.status(TrialFeature.ANALYTICS_GROUPING))
        clock = START + days(7)
        assertEquals(TrialStatus.Expired, trials.status(TrialFeature.ANALYTICS_GROUPING))
    }

    @Test
    fun `a clock set before the start reads as expired`() {
        trials.start(TrialFeature.ANALYTICS_GROUPING)
        clock = START - 1
        assertEquals(TrialStatus.Expired, trials.status(TrialFeature.ANALYTICS_GROUPING))
    }

    @Test
    fun `each feature has its own trial`() {
        trials.start(TrialFeature.ANALYTICS_GROUPING)
        clock += days(8)
        assertEquals(TrialStatus.Expired, trials.status(TrialFeature.ANALYTICS_GROUPING))
        assertEquals(TrialStatus.NotStarted, trials.status(TrialFeature.CSV_EXPORT))

        trials.start(TrialFeature.CSV_EXPORT)
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

        trials.start(TrialFeature.CSV_EXPORT)
        val during = access.first()
        assertTrue(during.unlocked)
        assertFalse(during.canStartTrial)
        assertEquals(7, during.trialDaysLeft)
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

/** [FeatureTrials] over an in-memory [stored] map of trial starts, read at [now]. */
internal fun testFeatureTrials(
    stored: MutableMap<String, Long> = mutableMapOf(),
    now: () -> Long = System::currentTimeMillis,
): FeatureTrials {
    val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    every { editor.putLong(any(), any()) } answers { stored[firstArg()] = secondArg(); editor }
    val prefs = mockk<SharedPreferences> {
        every { getLong(any(), any()) } answers { stored[firstArg()] ?: secondArg() }
        every { contains(any()) } answers { firstArg<String>() in stored }
        every { edit() } returns editor
    }
    return FeatureTrials(prefs, now)
}
