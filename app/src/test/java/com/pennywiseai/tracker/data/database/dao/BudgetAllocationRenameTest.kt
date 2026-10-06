package com.pennywiseai.tracker.data.database.dao

import com.pennywiseai.tracker.data.database.entity.BudgetCategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class BudgetAllocationRenameTest {

    private fun row(id: Long, budget: Long, name: String, amount: String, matchType: String? = null) =
        BudgetCategoryEntity(id = id, budgetId = budget, categoryName = name, budgetAmount = BigDecimal(amount), matchType = matchType)

    @Test
    fun `budget holding both names keeps one row with both amounts`() {
        val merges = allocationMergesForRename(
            listOf(row(1, 10, "Food", "500.50"), row(2, 10, "Eating out", "200")),
            oldName = "Food", newName = "Eating out"
        )
        assertEquals(listOf(AllocationMerge(keepId = 2, amount = BigDecimal("700.50"), dropId = 1)), merges)
    }

    @Test
    fun `no merge when only the old name is present, or in another budget`() {
        val merges = allocationMergesForRename(
            listOf(row(1, 10, "Food", "500"), row(2, 11, "Eating out", "200")),
            oldName = "Food", newName = "Eating out"
        )
        assertEquals(emptyList<AllocationMerge>(), merges)
    }

    @Test
    fun `type buckets are ignored`() {
        val merges = allocationMergesForRename(
            listOf(row(1, 10, "Food", "500"), row(2, 10, "Eating out", "200", matchType = "INVESTMENT")),
            oldName = "Food", newName = "Eating out"
        )
        assertEquals(emptyList<AllocationMerge>(), merges)
    }
}
