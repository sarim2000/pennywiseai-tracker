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

    @Test fun `lock off never locks`() {
        assertFalse(shouldLock(isEnabled = false, timeoutMinutes = 0, lastAuthTimestamp = 0L, unlockedSinceBackground = false, now = now))
        assertFalse(shouldLock(isEnabled = false, timeoutMinutes = 5, lastAuthTimestamp = 0L, unlockedSinceBackground = false, now = now))
    }

    @Test fun `unlocked on screen never locks, however long it's been`() {
        for (timeout in listOf(0, 1, 5, 15)) {
            assertFalse(shouldLock(isEnabled = true, timeoutMinutes = timeout, lastAuthTimestamp = now - 60 * MILLIS_PER_MINUTE, unlockedSinceBackground = true, now = now))
        }
    }

    @Test fun `immediately locks after a trip to the background, however short`() =
        assertTrue(shouldLock(isEnabled = true, timeoutMinutes = 0, lastAuthTimestamp = now - 1, unlockedSinceBackground = false, now = now))

    @Test fun `a timeout counts from leaving the app`() {
        assertFalse(shouldLock(isEnabled = true, timeoutMinutes = 5, lastAuthTimestamp = now - 4 * MILLIS_PER_MINUTE, unlockedSinceBackground = false, now = now))
        assertTrue(shouldLock(isEnabled = true, timeoutMinutes = 5, lastAuthTimestamp = now - 5 * MILLIS_PER_MINUTE, unlockedSinceBackground = false, now = now))
    }

    @Test fun `a timeout with no unlock recorded locks`() =
        assertTrue(shouldLock(isEnabled = true, timeoutMinutes = 5, lastAuthTimestamp = 0L, unlockedSinceBackground = false, now = now))

    @Test fun `a clock set back before the last unlock locks`() =
        assertTrue(shouldLock(isEnabled = true, timeoutMinutes = 5, lastAuthTimestamp = now + MILLIS_PER_MINUTE, unlockedSinceBackground = false, now = now))

    @Test fun `changing the timeout on screen never emits locked, whatever order the values land in`() {
        // Unlocked 90 s ago; setTimeoutMinutes sets the flag, then one DataStore write
        // whose two values reach the combined flow one at a time.
        for ((from, to) in listOf(1 to 5, 5 to 1, 15 to 1)) {
            val seen = emissionsOf(timeout = from, lastAuth = now - 90_000, unlocked = true) { timeout, lastAuth, unlocked ->
                unlocked.value = true
                timeout.value = to
                lastAuth.value = now
            }
            assertTrue("locked mid-change $from -> $to: $seen", seen.none { it })
        }
    }

    @Test fun `picking Immediately on screen does not lock the screen it was set on`() {
        val seen = emissionsOf(timeout = 1, lastAuth = now - 30_000, unlocked = true) { timeout, lastAuth, unlocked ->
            unlocked.value = true
            timeout.value = 0
            lastAuth.value = now
        }
        assertEquals(listOf(false), seen.distinct())
    }

    @Test fun `with Immediately, leaving the app locks and unlocking clears it`() {
        val seen = emissionsOf(timeout = 0, lastAuth = now, unlocked = true) { _, lastAuth, unlocked ->
            unlocked.value = false
            unlocked.value = true
            lastAuth.value = now + 1
        }
        assertEquals(listOf(false, true, false), seen.take(3))
        assertFalse(seen.last())
    }

    @Test fun `with a timeout, leaving the app stays unlocked until it runs out`() {
        // onAppBackgrounded: the leave time is written first, then the flag clears.
        val seen = emissionsOf(timeout = 5, lastAuth = now - 30 * MILLIS_PER_MINUTE, unlocked = true) { _, lastAuth, unlocked ->
            lastAuth.value = now
            unlocked.value = false
        }
        assertEquals(listOf(false), seen.distinct())
    }

    /** The lock flow's emissions while [changes] runs, from flows standing in for the settings. */
    private fun emissionsOf(
        timeout: Int,
        lastAuth: Long,
        unlocked: Boolean,
        changes: (MutableStateFlow<Int>, MutableStateFlow<Long>, MutableStateFlow<Boolean>) -> Unit
    ): List<Boolean> {
        val seen = mutableListOf<Boolean>()
        runTest(UnconfinedTestDispatcher()) {
            val timeoutFlow = MutableStateFlow(timeout)
            val lastAuthFlow = MutableStateFlow(lastAuth)
            val unlockedFlow = MutableStateFlow(unlocked)
            val job = launch {
                lockStateFlow(MutableStateFlow(true), timeoutFlow, lastAuthFlow, unlockedFlow) { now }.collect { seen += it }
            }
            changes(timeoutFlow, lastAuthFlow, unlockedFlow)
            job.cancel()
        }
        return seen
    }
}
