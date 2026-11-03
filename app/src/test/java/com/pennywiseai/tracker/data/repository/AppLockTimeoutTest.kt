package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.core.TimeConstants.MILLIS_PER_MINUTE
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
