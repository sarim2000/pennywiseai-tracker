package com.pennywiseai.tracker.presentation.add

import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The quick cash add: what the amount field accepts and the order of its category chips. */
class QuickAddActivityTest {

    @Test
    fun `digits from an Arabic keyboard become an amount`() {
        assertEquals("3.5", QuickAddActivity.normalizeAmount("٣٫٥"))
        assertEquals("12.750", QuickAddActivity.normalizeAmount("12,750"))
        assertEquals("", QuickAddActivity.normalizeAmount(""))
    }

    @Test
    fun `a second decimal point or a letter is refused`() {
        assertNull(QuickAddActivity.normalizeAmount("1.2.3"))
        assertNull(QuickAddActivity.normalizeAmount("1٫2."))
        assertNull(QuickAddActivity.normalizeAmount("-5"))
        assertNull(QuickAddActivity.normalizeAmount("5a"))
    }

    @Test
    fun `most-used categories lead and Others trails`() {
        val categories = listOf("Food", "Others", "Transport", "Groceries", "Bills")
            .map { CategoryEntity(name = it, color = "#000000") }
        val ranked = QuickAddActivity.rankCategories(categories, listOf("Others", "Groceries", "Food"), others = "Others")
        assertEquals(listOf("Groceries", "Food", "Transport", "Bills", "Others"), ranked.map { it.name })
    }
}
