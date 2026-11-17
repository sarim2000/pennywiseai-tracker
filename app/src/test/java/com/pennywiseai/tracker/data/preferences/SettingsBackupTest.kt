package com.pennywiseai.tracker.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.pennywiseai.tracker.data.backup.PennyWiseBackup
import com.pennywiseai.tracker.data.backup.PreferencesSnapshot
import com.pennywiseai.tracker.data.backup.SettingsSnapshot
import com.pennywiseai.tracker.data.backup.backupJson
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository.SettingsBackup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Every setting either travels with the backup and comes back on restore, or is listed as staying put. */
class SettingsBackupTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    @After
    fun tearDown() = scope.cancel()

    private fun newStore(name: String): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { File(tmp.root, "$name.preferences_pb") }
    )

    @Test
    fun `every preference key is classified exactly once`() {
        val declared = UserPreferencesRepository.PreferencesKeys::class.java.declaredFields
            .filter { Preferences.Key::class.java.isAssignableFrom(it.type) }
            .map { it.isAccessible = true; (it.get(null) as Preferences.Key<*>).name }
        val buckets = listOf(
            (SettingsBackup.BOOLEANS + SettingsBackup.INTS + SettingsBackup.LONGS + SettingsBackup.STRINGS + SettingsBackup.STRING_SETS).map { it.name },
            SettingsBackup.IN_LEGACY_SECTIONS.toList(),
            SettingsBackup.DEVICE_ONLY.toList()
        ).flatten()
        assertEquals("a key sits in two buckets", buckets.size, buckets.toSet().size)
        assertEquals("unclassified or stale keys", declared.toSet(), buckets.toSet())
    }

    @Test
    fun `settings round-trip through a backup into another install`() = runBlocking {
        val source = newStore("source")
        source.edit {
            it[stringPreferencesKey("theme_style")] = "BRANDED"
            it[stringPreferencesKey("app_font")] = "SERIF"
            it[booleanPreferencesKey("is_amoled_mode")] = true
            it[intPreferencesKey("budget_cycle_start_day")] = 25
            it[stringSetPreferencesKey("hidden_nav_tabs")] = setOf("chat", "analytics")
            it[stringPreferencesKey("user_name")] = "Sample"
            it[stringPreferencesKey("profile_image_uri")] = "avatar://3"
            it[longPreferencesKey("selected_profile_id")] = 7L
            it[longPreferencesKey("last_auth_timestamp")] = 123L
        }
        val snapshot = UserPreferencesRepository(source) {}.exportSettings(full = true)
        val decoded = backupJson.decodeFromString(
            PennyWiseBackup.serializer(),
            backupJson.encodeToString(PennyWiseBackup.serializer(), PennyWiseBackup(preferences = PreferencesSnapshot(settings = snapshot)))
        ).preferences.settings!!

        val target = newStore("target")
        target.edit {
            it[booleanPreferencesKey("dark_theme_enabled")] = true // unset in the source: resets
            it[booleanPreferencesKey("app_lock_enabled")] = true // device-only: kept
        }
        UserPreferencesRepository(target) {}.restoreSettings(decoded) { old -> if (old == 7L) 9L else null }

        val restored = target.data.first()
        assertEquals("BRANDED", restored[stringPreferencesKey("theme_style")])
        assertEquals("SERIF", restored[stringPreferencesKey("app_font")])
        assertEquals(true, restored[booleanPreferencesKey("is_amoled_mode")])
        assertEquals(25, restored[intPreferencesKey("budget_cycle_start_day")])
        assertEquals(setOf("chat", "analytics"), restored[stringSetPreferencesKey("hidden_nav_tabs")])
        assertEquals("Sample", restored[stringPreferencesKey("user_name")])
        assertEquals("avatar://3", restored[stringPreferencesKey("profile_image_uri")])
        assertEquals("profile id remapped", 9L, restored[longPreferencesKey("selected_profile_id")])
        assertNull("follow-system restored", restored[booleanPreferencesKey("dark_theme_enabled")])
        assertEquals(true, restored[booleanPreferencesKey("app_lock_enabled")])
        assertNull(restored[longPreferencesKey("last_auth_timestamp")])
    }

    @Test
    fun `masked export leaves out personal settings and restoring it keeps them`() = runBlocking {
        val source = newStore("source")
        source.edit { it[stringPreferencesKey("user_name")] = "Sample" }
        val masked = UserPreferencesRepository(source) {}.exportSettings(full = false)
        assertFalse("user_name" in masked.strings)
        assertTrue("theme_style" in masked.strings)

        val target = newStore("target")
        target.edit { it[stringPreferencesKey("user_name")] = "Local" }
        UserPreferencesRepository(target) {}.restoreSettings(masked) { it }
        assertEquals("Local", target.data.first()[stringPreferencesKey("user_name")])
    }

    @Test
    fun `a photo path, an unknown key or a wrong type is never written`() = runBlocking {
        val source = newStore("source")
        source.edit { it[stringPreferencesKey("profile_image_uri")] = "file:///data/photo.jpg" }
        assertFalse("profile_image_uri" in UserPreferencesRepository(source) {}.exportSettings(full = true).strings)

        val target = newStore("target")
        target.edit { it[stringPreferencesKey("profile_image_uri")] = "avatar://1" }
        UserPreferencesRepository(target) {}.restoreSettings(
            SettingsSnapshot(
                strings = mapOf("profile_image_uri" to "file:///x.jpg", "license_key" to "k", "dark_theme_enabled" to "x"),
                longs = mapOf("last_auth_timestamp" to 1L)
            )
        ) { it }
        val restored = target.data.first()
        assertEquals("avatar://1", restored[stringPreferencesKey("profile_image_uri")])
        assertEquals(1, restored.asMap().size)
    }

    @Test
    fun `an older backup has no settings block`() {
        val old = """{ "_format": "PennyWise Backup v1.3", "preferences": { "theme": { "is_dynamic_color_enabled": true } } }"""
        assertNull(backupJson.decodeFromString(PennyWiseBackup.serializer(), old).preferences.settings)
    }
}
