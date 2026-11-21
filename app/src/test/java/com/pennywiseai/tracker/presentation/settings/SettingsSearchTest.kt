package com.pennywiseai.tracker.presentation.settings

import com.pennywiseai.tracker.navigation.SettingsPage
import com.pennywiseai.tracker.navigation.SettingsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {

    private fun List<Pair<String, String>>.search(query: String) = matchingSearch(query, { it.first }, { it.second }).map { it.first }

    @Test
    fun `every settings page can be found`() {
        val targets = settingsSearchIndex.map { it.target }.toSet()
        SettingsSection.entries.forEach { assertTrue("$it missing", SettingsPage(it) in targets) }
    }

    @Test
    fun `no row is listed twice`() {
        val keys = settingsSearchIndex.map { it.title to it.target }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `case, punctuation, accents and Arabic marks do not matter`() {
        val items = listOf("Café mode" to "", "الإعدادات" to "", "عُملة العرض" to "", "الصفحة الرئيسية" to "")
        assertEquals(listOf("Café mode"), items.search("CAFE"))
        assertEquals(listOf("SMS check-up"), listOf("SMS check-up" to "").search("checkup"))
        assertEquals(listOf("الإعدادات"), items.search("الاعدادات"))
        assertEquals(listOf("عُملة العرض"), items.search("عمله"))
        assertEquals(listOf("الصفحة الرئيسية"), items.search("الرئيسيه"))
    }

    @Test
    fun `every word must match somewhere, title starts rank first`() {
        val items = listOf(
            "Export data" to "Backup & data",
            "Folder backup" to "Backup & data",
            "Backup now" to "Backup & data",
            "Date format" to "Language & formats",
        )
        assertEquals(listOf("Date format"), items.search("format date"))
        // Title starts with it, then title holds it, then only the page name does.
        assertEquals(listOf("Backup now", "Folder backup", "Export data"), items.search("backup"))
        // Dropped punctuation leaves no double space to break "starts with".
        assertEquals(listOf("Backup & data", "Data backup"), listOf("Data backup" to "", "Backup & data" to "").search("backup data"))
        assertTrue(items.search("   ").isEmpty())
    }
}
