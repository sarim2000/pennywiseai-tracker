package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.core.TimeConstants.MILLIS_PER_MINUTE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockTimeoutTest {
    private val now = 1_000_000_000L
    private val onScreen = LockSession(unlocked = true)
    private val away = LockSession(unlocked = false)

    @Test fun `lock off never locks`() {
        assertFalse(shouldLock(isEnabled = false, timeoutMinutes = 0, lastAuthTimestamp = 0L, session = away, now = now))
        assertFalse(shouldLock(isEnabled = false, timeoutMinutes = 5, lastAuthTimestamp = 0L, session = away, now = now))
    }

    @Test fun `unlocked on screen never locks, however long it's been`() {
        for (timeout in listOf(0, 1, 5, 15)) {
            assertFalse(shouldLock(isEnabled = true, timeoutMinutes = timeout, lastAuthTimestamp = now - 60 * MILLIS_PER_MINUTE, session = onScreen, now = now))
        }
    }

    @Test fun `immediately locks after a trip to the background, however short`() =
        assertTrue(shouldLock(isEnabled = true, timeoutMinutes = 0, lastAuthTimestamp = now - 1, session = away, now = now))

    @Test fun `a timeout counts from leaving the app`() {
        assertFalse(shouldLock(isEnabled = true, timeoutMinutes = 5, lastAuthTimestamp = now - 4 * MILLIS_PER_MINUTE, session = away, now = now))
        assertTrue(shouldLock(isEnabled = true, timeoutMinutes = 5, lastAuthTimestamp = now - 5 * MILLIS_PER_MINUTE, session = away, now = now))
    }

    @Test fun `the leave time in memory counts before it's persisted`() {
        val left = onScreen.left(now - MILLIS_PER_MINUTE)
        assertFalse(shouldLock(isEnabled = true, timeoutMinutes = 5, lastAuthTimestamp = now - 30 * MILLIS_PER_MINUTE, session = left, now = now))
    }

    @Test fun `a timeout with no unlock recorded locks`() =
        assertTrue(shouldLock(isEnabled = true, timeoutMinutes = 5, lastAuthTimestamp = 0L, session = away, now = now))

    @Test fun `a clock set back before the last unlock locks`() =
        assertTrue(shouldLock(isEnabled = true, timeoutMinutes = 5, lastAuthTimestamp = now + MILLIS_PER_MINUTE, session = away, now = now))

    @Test fun `leaving unlocked ends the session and records when`() {
        val left = onScreen.left(now)
        assertFalse(left.unlocked)
        assertEquals(now, left.leftAt)
        assertEquals(onScreen.trips + 1, left.trips)
    }

    @Test fun `leaving while locked doesn't restart the timeout`() =
        assertEquals(now - 10_000, LockSession(unlocked = false, leftAt = now - 10_000).left(now).leftAt)

    @Test fun `a check from before the app left can't resume the session`() {
        val checkStarted = onScreen.trips
        val left = onScreen.left(now)
        assertFalse(left.resumed(checkStarted).unlocked)
        assertTrue(left.resumed(left.trips).unlocked)
    }

    @Test fun `changing the timeout on screen never emits locked, whatever order the values land in`() {
        // Unlocked 90 s ago; one settings write whose two values reach the combined
        // flow one at a time, in either order.
        for ((from, to) in listOf(1 to 5, 5 to 1, 15 to 1)) {
            for (timeoutFirst in listOf(true, false)) {
                val seen = emissionsOf(timeout = from, lastAuth = now - 90_000, session = onScreen) { timeout, lastAuth, _ ->
                    if (timeoutFirst) timeout.value = to
                    lastAuth.value = now
                    if (!timeoutFirst) timeout.value = to
                }
                assertTrue("locked mid-change $from -> $to: $seen", seen.none { it })
            }
        }
    }

    @Test fun `with Immediately, leaving the app locks and unlocking clears it`() {
        val seen = emissionsOf(timeout = 0, lastAuth = now, session = onScreen) { _, lastAuth, session ->
            session.value = session.value.left(now)
            session.value = session.value.unlockedNow()
            lastAuth.value = now + 1
        }
        assertEquals(listOf(false, true, false), seen.take(3))
        assertFalse(seen.last())
    }

    @Test fun `with a timeout, leaving the app stays unlocked until it runs out`() {
        val seen = emissionsOf(timeout = 5, lastAuth = now - 30 * MILLIS_PER_MINUTE, session = onScreen) { _, _, session ->
            session.value = session.value.left(now)
        }
        assertEquals(listOf(false), seen.distinct())
    }

    /** The lock flow's emissions while [changes] runs, from flows standing in for the settings. */
    private fun emissionsOf(
        timeout: Int,
        lastAuth: Long,
        session: LockSession,
        changes: (MutableStateFlow<Int>, MutableStateFlow<Long>, MutableStateFlow<LockSession>) -> Unit
    ): List<Boolean> {
        val seen = mutableListOf<Boolean>()
        runTest(UnconfinedTestDispatcher()) {
            val timeoutFlow = MutableStateFlow(timeout)
            val lastAuthFlow = MutableStateFlow(lastAuth)
            val sessionFlow = MutableStateFlow(session)
            val job = launch {
                lockStateFlow(MutableStateFlow(true), timeoutFlow, lastAuthFlow, sessionFlow) { now }.collect { seen += it }
            }
            changes(timeoutFlow, lastAuthFlow, sessionFlow)
            job.cancel()
        }
        return seen
    }
}
