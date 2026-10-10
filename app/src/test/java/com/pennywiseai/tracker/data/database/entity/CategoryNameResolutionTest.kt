package com.pennywiseai.tracker.data.database.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryNameResolutionTest {

    private fun cat(name: String, system: Boolean = false, systemName: String? = null) =
        CategoryEntity(name = name, color = "#000000", isSystem = system, systemName = systemName)

    @Test
    fun `a renamed built-in takes over its original name`() {
        val cats = listOf(cat("Eating out", system = true, systemName = "Food & Dining"), cat("Groceries", true, "Groceries"))
        assertEquals("Eating out", cats.currentNameFor("Food & Dining"))
        assertEquals("Groceries", cats.currentNameFor("Groceries"))
    }

    @Test
    fun `a category actually named so wins over a renamed built-in`() {
        // The user renamed the built-in, then created their own "Food & Dining".
        val cats = listOf(cat("Eating out", true, "Food & Dining"), cat("Food & Dining"))
        assertEquals("Food & Dining", cats.currentNameFor("Food & Dining"))
    }

    @Test
    fun `unknown names pass through`() {
        assertEquals("Pets", listOf(cat("Eating out", true, "Food & Dining")).currentNameFor("Pets"))
    }

    @Test
    fun `builtinKey falls back to the name for pre-migration system rows`() {
        assertEquals("Shopping", cat("Shopping", system = true).builtinKey)
        assertEquals("Food & Dining", cat("Eating out", true, "Food & Dining").builtinKey)
        assertNull(cat("Pets").builtinKey)
    }

    @Test
    fun `identity lookup survives a built-in renamed to another's original name`() {
        // Groceries -> Pantry, then Food & Dining -> Groceries.
        val cats = listOf(cat("Groceries", true, "Food & Dining"), cat("Pantry", true, "Groceries"))
        assertEquals("Groceries", cats.nameForBuiltin("Food & Dining"))
        assertEquals("Pantry", cats.nameForBuiltin("Groceries"))
        assertEquals("Pets", cats.nameForBuiltin("Pets"))
    }

    @Test
    fun `mirror translates the auto-categorizer's names by identity`() {
        com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames.update(
            listOf(cat("Groceries", true, "Food & Dining"), cat("Pantry", true, "Groceries"), cat("Shopping", system = true))
        )
        try {
            val names = com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames
            assertEquals("Groceries", names.current("Food & Dining"))
            assertEquals("Pantry", names.current("Groceries"))
            assertEquals("Shopping", names.current("Shopping")) // not renamed
            assertEquals("Others", names.current("Others"))
        } finally {
            com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames.update(emptyList())
        }
    }
}
