package com.pennywiseai.tracker.data.database.dao

import com.pennywiseai.tracker.data.database.entity.BudgetCategoryEntity
import java.math.BigDecimal

/** What to write so a budget that holds both names keeps one row with both amounts. */
internal data class AllocationMerge(val keepId: Long, val amount: BigDecimal, val dropId: Long)

/**
 * Budgets where renaming [oldName] → [newName] would collide with the
 * `(budget_id, category_name)` unique index because the budget already has a
 * [newName] row (left over from a deleted category). Rather than dropping one
 * row's amount, the [newName] row absorbs the [oldName] row's amount.
 */
internal fun allocationMergesForRename(
    rows: List<BudgetCategoryEntity>,
    oldName: String,
    newName: String
): List<AllocationMerge> =
    rows.filter { it.matchType == null }
        .groupBy { it.budgetId }
        .values
        .mapNotNull { inBudget ->
            val old = inBudget.firstOrNull { it.categoryName == oldName } ?: return@mapNotNull null
            val existing = inBudget.firstOrNull { it.categoryName == newName } ?: return@mapNotNull null
            AllocationMerge(existing.id, existing.budgetAmount + old.budgetAmount, old.id)
        }
