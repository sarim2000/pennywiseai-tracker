package com.pennywiseai.tracker.data.backup

import android.content.Context
import com.pennywiseai.tracker.core.entitlement.LicenseRestorer
import com.pennywiseai.tracker.data.preferences.StoredLicense
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** A backup's license reaches the license rail only when this install has none. */
class BackupLicenseRestoreTest {
    private val prefs = mockk<UserPreferencesRepository>(relaxed = true)

    // importPreferences also restores bank-account merges into SharedPreferences.
    private val context = mockk<Context>(relaxed = true) {
        every { getSharedPreferences(any(), any()).edit().putStringSet(any(), any()).commit() } returns true
    }
    private val restorer = mockk<LicenseRestorer>(relaxed = true)
    private val importer = BackupImporter(context, mockk(relaxed = true), prefs, restorer, mockk(relaxed = true))

    private fun snapshot(key: String?, instanceId: String?) =
        PreferencesSnapshot(app = AppPreferences(licenseKey = key, licenseInstanceId = instanceId))

    @Test
    fun backupLicenseIsRestoredWhenThisInstallHasNone() = runBlocking {
        every { prefs.storedLicense } returns flowOf(null)
        importer.importPreferences(snapshot(key = "TEST-KEY", instanceId = "old-instance"))
        coVerify(exactly = 1) { restorer.restore("TEST-KEY", "old-instance") }
    }

    @Test
    fun backupLicenseWithoutInstanceIsStillRestored() = runBlocking {
        every { prefs.storedLicense } returns flowOf(null)
        importer.importPreferences(snapshot(key = "TEST-KEY", instanceId = null))
        coVerify(exactly = 1) { restorer.restore("TEST-KEY", null) }
    }

    @Test
    fun aLicenseActivatedHereIsNeverReplaced() = runBlocking {
        every { prefs.storedLicense } returns flowOf(StoredLicense("LOCAL-KEY", "here", 1L, null))
        importer.importPreferences(snapshot(key = "TEST-KEY", instanceId = "old-instance"))
        coVerify(exactly = 0) { restorer.restore(any(), any()) }
    }

    @Test
    fun backupWithoutLicenseRestoresNothing() = runBlocking {
        importer.importPreferences(snapshot(key = null, instanceId = null))
        coVerify(exactly = 0) { restorer.restore(any(), any()) }
    }
}
