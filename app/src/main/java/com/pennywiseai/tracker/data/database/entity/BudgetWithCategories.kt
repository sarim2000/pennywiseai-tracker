package com.pennywiseai.tracker.data.database.entity

import androidx.room.Embedded
import androidx.room.Relation
import java.math.BigDecimal

data class BudgetWithCategories(
    @Embedded val budget: BudgetEntity,
    @Relation(parentColumn = "id", entityColumn = "budget_id")
    val categories: List<BudgetCategoryEntity>
) {
    val totalBudgetAmount: BigDecimal
        get() = categories.sumOf { it.budgetAmount }
}
