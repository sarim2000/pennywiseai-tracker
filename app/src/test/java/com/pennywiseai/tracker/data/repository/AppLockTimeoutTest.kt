package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.core.TimeConstants.MILLIS_PER_MINUTE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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

    @Test fun `immediately stays unlocked until the app leaves the screen`() =
        assertFalse(shouldLock(isEnabled = true, timeoutMinutes = 0, lastAuthTimestamp = now - 60 * MILLIS_PER_MINUTE, unlockedSinceBackground = true, now = now))

    @Test fun `immediately locks after a trip to the background, however short`() =
        assertTrue(shouldLock(isEnabled = true, timeoutMinutes = 0, lastAuthTimestamp = now - 1, unlockedSinceBackground = false, now = now))

    @Test fun `a timeout locks once it has passed since the last unlock`() {
        assertFalse(shouldLock(isEnabled = true, timeoutMinutes = 5, lastAuthTimestamp = now - 4 * MILLIS_PER_MINUTE, unlockedSinceBackground = false, now = now))
        assertTrue(shouldLock(isEnabled = true, timeoutMinutes = 5, lastAuthTimestamp = now - 5 * MILLIS_PER_MINUTE, unlockedSinceBackground = true, now = now))
    }

    @Test fun `a timeout with no unlock recorded locks`() =
        assertTrue(shouldLock(isEnabled = true, timeoutMinutes = 5, lastAuthTimestamp = 0L, unlockedSinceBackground = true, now = now))

    @Test fun `changing a passed one-minute timeout to five never emits locked`() {
        // Unlocked 90 s ago, then a trip out and back inside the minute cleared the flag.
        val seen = emissionsOf(timeout = 1, lastAuth = now - 90_000, unlocked = false) { timeout, lastAuth, unlocked ->
            // setTimeoutMinutes: the flag first, then one DataStore write of both values.
            unlocked.value = true
            timeout.value = 5
            lastAuth.value = now
        }
        assertTrue(seen.first()) // the initial check: the minute has passed
        assertTrue("locked mid-change: $seen", seen.drop(1).none { it })
    }

    @Test fun `picking Immediately after a trip out does not lock the screen it was set on`() {
        val seen = emissionsOf(timeout = 1, lastAuth = now - 30_000, unlocked = false) { timeout, lastAuth, unlocked ->
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
                lockStateFlow(combine(MutableStateFlow(true), timeoutFlow, lastAuthFlow, ::Triple), unlockedFlow) { now }.collect { seen += it }
            }
            changes(timeoutFlow, lastAuthFlow, unlockedFlow)
            job.cancel()
        }
        return seen
    }

    @Test
    fun `turning the lock on with a fresh unlock time never locks`() {
        val seen = mutableListOf<Boolean>()
        runTest(UnconfinedTestDispatcher()) {
            val settings = MutableStateFlow(Triple(false, 1, 0L))
            val job = launch { lockStateFlow(settings, MutableStateFlow(false)) { now }.collect { seen += it } }
            settings.value = Triple(true, 1, now) // enabled + unlock time land in one write
            job.cancel()
        }
        assertEquals(listOf(false), seen.distinct())
    }
}
