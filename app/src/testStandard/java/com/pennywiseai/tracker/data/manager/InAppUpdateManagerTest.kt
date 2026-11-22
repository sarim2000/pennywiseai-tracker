package com.pennywiseai.tracker.data.manager

import com.google.android.play.core.install.model.InstallStatus
import com.pennywiseai.tracker.data.manager.InAppUpdateManager.Companion.shouldOffer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppUpdateManagerTest {
    @Test
    fun `offers a new version once, then only a newer one`() {
        assertTrue(shouldOffer(true, InstallStatus.UNKNOWN, availableVersion = 200, offeredVersion = -1))
        assertFalse(shouldOffer(true, InstallStatus.UNKNOWN, availableVersion = 200, offeredVersion = 200))
        assertTrue(shouldOffer(true, InstallStatus.UNKNOWN, availableVersion = 201, offeredVersion = 200))
    }

    @Test
    fun `never offers while Play is already fetching or installing`() {
        for (status in listOf(InstallStatus.PENDING, InstallStatus.DOWNLOADING, InstallStatus.INSTALLING)) {
            assertFalse(shouldOffer(true, status, availableVersion = 200, offeredVersion = -1))
        }
    }

    @Test
    fun `no offer without an allowed update`() {
        assertFalse(shouldOffer(false, InstallStatus.UNKNOWN, availableVersion = 200, offeredVersion = -1))
    }
}
