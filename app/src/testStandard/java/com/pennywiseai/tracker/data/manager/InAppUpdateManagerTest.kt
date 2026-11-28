package com.pennywiseai.tracker.data.manager

import com.google.android.play.core.install.model.InstallStatus
import com.pennywiseai.tracker.data.manager.InAppUpdateManager.Companion.shouldOffer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppUpdateManagerTest {
    private fun offer(
        availableVersion: Int = 200,
        declinedVersion: Int = -1,
        startedVersion: Int = -1,
        installStatus: Int = InstallStatus.UNKNOWN,
        updateAvailable: Boolean = true
    ) = shouldOffer(updateAvailable, installStatus, availableVersion, declinedVersion, startedVersion)

    @Test
    fun `a declined version is not offered again, a newer one is`() {
        assertTrue(offer())
        assertFalse(offer(declinedVersion = 200))
        assertTrue(offer(availableVersion = 201, declinedVersion = 200))
    }

    @Test
    fun `a sheet already shown this process is not shown again`() {
        assertFalse(offer(startedVersion = 200))
        assertTrue(offer(availableVersion = 201, startedVersion = 200))
    }

    @Test
    fun `never offers while Play is already fetching or installing`() {
        for (status in listOf(InstallStatus.PENDING, InstallStatus.DOWNLOADING, InstallStatus.INSTALLING)) {
            assertFalse(offer(installStatus = status))
        }
    }

    @Test
    fun `no offer without an allowed update`() {
        assertFalse(offer(updateAvailable = false))
    }
}
