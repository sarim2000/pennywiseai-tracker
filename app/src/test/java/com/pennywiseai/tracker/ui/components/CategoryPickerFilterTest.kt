package com.pennywiseai.tracker.ui.components

import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/** #876: every category picker offers only the transaction's type, hidden ones left out. */
class CategoryPickerFilterTest {
    private val all = listOf(
        CategoryEntity(id = 1, name = "Food", color = "#000000"),
        CategoryEntity(id = 2, name = "Fruit", color = "#000000", parentId = 1),
        CategoryEntity(id = 3, name = "Old", color = "#000000", isHidden = true),
        CategoryEntity(id = 4, name = "Salary", color = "#000000", isIncome = true),
        CategoryEntity(id = 5, name = "Refunds", color = "#000000", isIncome = true, isHidden = true)
    )

    @Test
    fun `expense pickers show expense categories in order`() =
        assertEquals(listOf("Food", "Fruit"), all.forPicker(isIncome = false).map { it.name })

    @Test
    fun `a type switch picks the built-in under its current name, or the first offered`() {
        val renamed = all + CategoryEntity(id = 6, name = "Earnings", color = "#000000", isIncome = true, isSystem = true, systemName = "Income")
        assertEquals("Earnings", renamed.pickerDefault("Income", isIncome = true))
        assertEquals("Food", all.pickerDefault("Others", isIncome = false))
    }

    @Test
    fun `most used follows usage order, skips what the picker doesn't offer, and caps at the limit`() {
        val offered = all.forPicker(isIncome = false)
        assertEquals(listOf("Fruit", "Food"), offered.mostUsed(listOf("Salary", "Fruit", "Gone", "Food")).map { it.name })
        assertEquals(listOf("Fruit"), offered.mostUsed(listOf("Fruit", "Food"), limit = 1).map { it.name })
        assertEquals(emptyList<String>(), offered.mostUsed(emptyList()).map { it.name })
    }

    @Test
    fun `search matches the shown translated name or the stored one, ignoring case`() {
        val offered = all.forPicker(isIncome = false)
        val label = { c: CategoryEntity -> if (c.name == "Food") "Essen" else c.name }
        assertEquals(listOf("Food"), offered.matching("ess", label).map { it.name })
        assertEquals(listOf("Food"), offered.matching(" food ", label).map { it.name })
        assertEquals(offered, offered.matching("  ", label))
    }

    @Test
    fun `income pickers show income categories`() =
        assertEquals(listOf("Salary"), all.forPicker(isIncome = true).map { it.name })
}
