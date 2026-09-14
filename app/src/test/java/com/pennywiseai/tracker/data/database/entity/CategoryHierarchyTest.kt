package com.pennywiseai.tracker.data.database.entity

import org.junit.Assert.assertEquals
import org.junit.Test

/** Sub-categories (#374): ordering, parent lookup and filter expansion. */
class CategoryHierarchyTest {
    private fun cat(id: Long, name: String, parent: Long? = null) =
        CategoryEntity(id = id, name = name, color = "#000000", parentId = parent)

    private val cats = listOf(
        cat(1, "Food"), cat(2, "Coffee", 1), cat(3, "Travel"), cat(4, "Groceries", 1), cat(5, "Orphan", 99)
    )

    @Test
    fun `hierarchical puts children right after their parent and keeps orphans top-level`() {
        assertEquals(listOf("Food", "Coffee", "Groceries", "Travel", "Orphan"), cats.hierarchical().map { it.name })
    }

    @Test
    fun `parentNameOf maps child names to parent names only`() {
        assertEquals(mapOf("Coffee" to "Food", "Groceries" to "Food"), cats.parentNameOf())
    }

    @Test
    fun `expandWithChildren adds a parent's children but never a child's siblings`() {
        assertEquals(setOf("Food", "Coffee", "Groceries", "Travel"), cats.expandWithChildren(listOf("Food", "Travel")))
        assertEquals(setOf("Coffee"), cats.expandWithChildren(listOf("Coffee")))
    }
}
