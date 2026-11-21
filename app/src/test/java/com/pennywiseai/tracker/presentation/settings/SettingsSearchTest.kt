package com.pennywiseai.tracker.presentation.settings

import android.os.Build
import com.lemonappdev.konsist.api.Konsist
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.navigation.SettingsPage
import com.pennywiseai.tracker.navigation.SettingsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.pennywiseai.feature.learnedcategories.R as LearnedCategoriesR

class SettingsSearchTest {

    /** Fields as the screen passes them: title, subtitle, keywords, page name. */
    private fun List<List<String>>.search(query: String) = matchingSearch(query) { it }.map { it.first() }

    private fun rows(vararg titles: String) = titles.map { listOf(it, "", "", "") }

    @Test
    fun `every settings page can be found`() {
        val targets = settingsSearchIndex().map { it.target }.toSet()
        SettingsSection.entries.forEach { assertTrue("$it missing", SettingsPage(it) in targets) }
    }

    @Test
    fun `no row is listed twice`() {
        val keys = settingsSearchIndex().map { it.title to it.target }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `every row on a settings page is indexed or skipped on purpose`() {
        // Android 12, so rows Appearance shows only from then (Blur Effects) still have to be indexed.
        val indexed = settingsSearchIndex(Build.VERSION_CODES.S).flatMap { listOf(it.title) + it.keywords }.toSet()
        val rowTitle = Regex("""(?<!sub)title = stringResource\((\w+)\.string\.(\w+)""")
        val pages = Konsist.scopeFromPackage("com.pennywiseai.tracker.presentation.settings", "app", "main").files
            .filter { it.name.matches(Regex("Settings\\w*Pages?|SettingsScreen|AppearanceScreen")) }
        assertTrue("no settings page files found. Did they move? Point this test at them.", pages.size >= 6)
        val missing = pages.flatMap { file ->
            rowTitle.findAll(file.text).map { it.groupValues[1] to it.groupValues[2] }
                .filterNot { (_, name) -> name in NOT_SEARCHABLE }
                .filterNot { (rClass, name) ->
                    val strings = if (rClass == "LearnedCategoriesR") LearnedCategoriesR.string::class.java else R.string::class.java
                    strings.getField(name).getInt(null) in indexed
                }
                .map { (_, name) -> "${file.name}: $name" }
                .toList()
        }.distinct()
        assertTrue(
            "Settings rows missing from settingsSearchIndex (SettingsSearch.kt). Index them, " +
                "or add them to NOT_SEARCHABLE with the reason:\n" + missing.joinToString("\n") { "  $it" },
            missing.isEmpty(),
        )
    }

    @Test
    fun `case, punctuation, accents and Arabic marks do not matter`() {
        val items = rows("Café mode", "الإعدادات", "عُملة العرض", "الصفحة الرئيسية")
        assertEquals(listOf("Café mode"), items.search("CAFE"))
        assertEquals(listOf("SMS check-up"), rows("SMS check-up").search("checkup"))
        assertEquals(listOf("الإعدادات"), items.search("الاعدادات"))
        assertEquals(listOf("عُملة العرض"), items.search("عمله"))
        assertEquals(listOf("الصفحة الرئيسية"), items.search("الرئيسيه"))
    }

    @Test
    fun `every word must match somewhere`() {
        val items = listOf(
            listOf("Date format", "", "", "Language & formats"),
            listOf("Dark", "", "dark mode, night mode", "Appearance"),
        )
        assertEquals(listOf("Date format"), items.search("format date"))
        assertEquals(listOf("Dark"), items.search("dark mode"))
        assertTrue(items.search("date dark").isEmpty())
        assertTrue(items.search("   ").isEmpty())
    }

    @Test
    fun `exact title, then title start, then word starts, then mid-word`() {
        assertEquals(
            listOf("Language", "Language & formats", "App language"),
            rows("App language", "Language & formats", "Language").search("language"),
        )
        assertEquals(listOf("Default currency", "Recurring"), rows("Recurring", "Default currency").search("curr"))
        // A word start in the page name beats a hit inside a title word.
        val ai = listOf(listOf("Daily summary", "", "", "Notifications"), listOf("Connect Claude", "", "", "AI"))
        assertEquals(listOf("Connect Claude", "Daily summary"), ai.search("ai"))
        // Title before subtitle before page name.
        val backup = listOf(
            listOf("Export data", "", "", "Backup & data"),
            listOf("Import data", "Restore a backup file", "", "Backup & data"),
            listOf("Folder backup", "", "", "Backup & data"),
        )
        assertEquals(listOf("Folder backup", "Import data", "Export data"), backup.search("backup"))
        // Dropped punctuation leaves no double space to break "starts with".
        assertEquals(listOf("Backup & data", "Data backup"), rows("Data backup", "Backup & data").search("backup data"))
    }

    @Test
    fun `amoled terms find dark, the choice that shows the control`() {
        val index = settingsSearchIndex()
        assertTrue(index.none { it.title == R.string.appearance_amoled_title })
        val dark = index.single { it.title == R.string.appearance_mode_dark }
        assertTrue(R.string.appearance_amoled_title in dark.keywords)
        assertTrue(R.string.appearance_amoled_subtitle in dark.keywords)
    }

    @Test
    fun `blur effects is searchable only on Android 12 and up`() {
        val blur = R.string.appearance_blur_title
        assertTrue(settingsSearchIndex(Build.VERSION_CODES.S).any { it.title == blur })
        assertTrue(settingsSearchIndex(Build.VERSION_CODES.S - 1).none { it.title == blur })
    }

    private companion object {
        /** Settings rows search leaves out on purpose, and why. */
        val NOT_SEARCHABLE = setOf(
            // Group headers inside a page, not rows.
            "settings_group_sms", "settings_group_app_notifications", "settings_contacts_section",
            "settings_group_backup", "settings_group_auto_backup", "settings_group_import", "settings_group_danger",
            "appearance_accessibility_section",
            // The Appearance screen's own bar title; the page is indexed under its hub name.
            "appearance_title",
            // Need something outside their page: an account, a hidden Plan tab, the app
            // language, developer mode, a missing notification permission.
            "settings_main_account_title", "settings_budgets_title", "subscriptions_title", "conscience_title",
            "mascot_playground_title", "settings_notifications_permission_title",
            // On the hub itself, above the search results' starting point; differs by flavor.
            "support_title",
            // Help links leave the app.
            "settings_user_guide_title", "settings_report_issue_title", "home_menu_discord", "home_menu_rate",
        )
    }
}
