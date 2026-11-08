package com.pennywiseai.tracker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockCoverTest {
    @Test fun `covered until the first lock check, lock on or off`() =
        assertTrue(coverForLock(checks = 0, isLockEnabled = false, isLocked = false, unlockedOnScreen = false, onLockScreen = false))

    @Test fun `lock off never covers once checked`() =
        assertFalse(coverForLock(checks = 3, isLockEnabled = false, isLocked = false, unlockedOnScreen = false, onLockScreen = false))

    @Test fun `back from the background, covered until the check resumes the session`() {
        assertTrue(coverForLock(checks = 3, isLockEnabled = true, isLocked = false, unlockedOnScreen = false, onLockScreen = false))
        assertFalse(coverForLock(checks = 4, isLockEnabled = true, isLocked = false, unlockedOnScreen = true, onLockScreen = false))
    }

    @Test fun `locked, covered until the lock screen is up, then not over it`() {
        assertTrue(coverForLock(checks = 4, isLockEnabled = true, isLocked = true, unlockedOnScreen = false, onLockScreen = false))
        assertFalse(coverForLock(checks = 4, isLockEnabled = true, isLocked = true, unlockedOnScreen = false, onLockScreen = true))
    }

    @Test fun `a rotation keeps the session, so nothing is covered`() =
        assertFalse(coverForLock(checks = 5, isLockEnabled = true, isLocked = false, unlockedOnScreen = true, onLockScreen = false))
}
