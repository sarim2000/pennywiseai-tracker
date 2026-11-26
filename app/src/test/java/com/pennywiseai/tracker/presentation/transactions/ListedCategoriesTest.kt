package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The list behind a budget's parent line leaves out a sub-category with its own line (#1425). */
class ListedCategoriesTest {

    private val all = listOf(
        CategoryEntity(id = 1, name = "Food & Dining", color = "#000000"),
        CategoryEntity(id = 2, name = "Snacks", color = "#000000", parentId = 1),
        CategoryEntity(id = 3, name = "Coffee", color = "#000000", parentId = 1),
        CategoryEntity(id = 4, name = "Travel", color = "#000000")
    )

    // The budget lists Food & Dining, Snacks and Travel.
    private val budgetLine = "Food & Dining" to setOf("Snacks", "Travel")

    @Test
    fun `a parent's line opens its list without the sub-category rows that have their own line`() {
        assertEquals(setOf("Food & Dining", "Coffee"), listedCategories("Food & Dining", null, all, budgetLine))
    }

    @Test
    fun `without a budget line, a parent's list keeps every sub-category`() {
        assertEquals(setOf("Food & Dining", "Snacks", "Coffee"), listedCategories("Food & Dining", null, all, null))
    }

    @Test
    fun `the left-out rows apply only to the category they came with`() {
        assertEquals(setOf("Food & Dining", "Snacks", "Coffee"), listedCategories(null, listOf("Food & Dining"), all, budgetLine))
        assertEquals(setOf("Travel"), listedCategories("Travel", null, all, budgetLine))
        assertNull(listedCategories(null, null, all, budgetLine))
    }
}
