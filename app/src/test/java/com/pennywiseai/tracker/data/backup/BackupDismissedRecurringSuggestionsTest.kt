package com.pennywiseai.tracker.data.backup

import android.content.Context
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Test

/** "Not recurring" answers travel with the backup; older backups restore none. */
class BackupDismissedRecurringSuggestionsTest {
    private val prefs = mockk<UserPreferencesRepository>(relaxed = true)
    // importPreferences also restores bank-account merges into SharedPreferences.
    private val context = mockk<Context>(relaxed = true) {
        every { getSharedPreferences(any(), any()).edit().putStringSet(any(), any()).commit() } returns true
    }
    private val importer = BackupImporter(context, mockk(relaxed = true), prefs, mockk(relaxed = true), mockk(relaxed = true))

    @Test
    fun olderBackupWithoutTheKeyDecodesToEmpty() {
        val backup = backupJson.decodeFromString<PennyWiseBackup>("""{ "preferences": { "app": { "has_shown_scan_tutorial": true } } }""")
        assertEquals(emptyList<String>(), backup.preferences.app.dismissedRecurringSuggestions)
    }

    @Test
    fun dismissedSuggestionsRoundTripAndRestoreAdditively() = runBlocking {
        val app = AppPreferences(dismissedRecurringSuggestions = listOf("USD|streamco"))
        val decoded = backupJson.decodeFromString<AppPreferences>(backupJson.encodeToString(AppPreferences.serializer(), app))
        assertEquals(listOf("USD|streamco"), decoded.dismissedRecurringSuggestions)

        importer.importPreferences(PreferencesSnapshot(app = decoded))
        coVerify(exactly = 1) { prefs.addDismissedRecurringSuggestions(listOf("USD|streamco")) }
    }
}
