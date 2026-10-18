package com.pennywiseai.tracker.data.backup

import android.content.Context
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** #912: hidden bottom-bar tabs travel with the backup; older backups leave them alone. */
class BackupHiddenNavTabsTest {
    private val prefs = mockk<UserPreferencesRepository>(relaxed = true)
    // importPreferences also restores bank-account merges into SharedPreferences.
    private val context = mockk<Context>(relaxed = true) {
        every { getSharedPreferences(any(), any()).edit().putStringSet(any(), any()).commit() } returns true
    }
    private val importer = BackupImporter(context, mockk(relaxed = true), prefs, mockk(relaxed = true), mockk(relaxed = true))

    // Shaped like a backup written before the field existed.
    private val olderBackup = """{ "preferences": { "theme": { "is_dark_theme_enabled": true, "is_dynamic_color_enabled": false } } }"""

    @Test
    fun olderBackupWithoutTheKeyDecodesToNull() {
        val backup = backupJson.decodeFromString<PennyWiseBackup>(olderBackup)
        assertNull(backup.preferences.theme.hiddenNavTabs)
    }

    @Test
    fun restoringAnOlderBackupLeavesTheCurrentTabChoiceAlone() = runBlocking {
        importer.importPreferences(backupJson.decodeFromString<PennyWiseBackup>(olderBackup).preferences)
        coVerify(exactly = 0) { prefs.updateHiddenNavTabs(any()) }
    }

    @Test
    fun hiddenTabsRoundTripAndRestore() = runBlocking {
        val theme = ThemePreferences(hiddenNavTabs = listOf("chat"))
        val decoded = backupJson.decodeFromString<ThemePreferences>(backupJson.encodeToString(ThemePreferences.serializer(), theme))
        assertEquals(listOf("chat"), decoded.hiddenNavTabs)

        importer.importPreferences(PreferencesSnapshot(theme = decoded))
        coVerify(exactly = 1) { prefs.updateHiddenNavTabs(setOf("chat")) }
    }
}
