package com.pennywiseai.tracker.data.database.entity

import androidx.room3.Embedded
import androidx.room3.Relation
import java.math.BigDecimal

data class BudgetWithCategories(
    @Embedded val budget: BudgetEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["budget_id"])
    val categories: List<BudgetCategoryEntity>
) {
    val totalBudgetAmount: BigDecimal
        get() {
            val overallAmount = budget.limitAmount
            return if (overallAmount > BigDecimal.ZERO) overallAmount
            else categories.fold(BigDecimal.ZERO) { acc, cat -> acc + cat.budgetAmount }
        }
}
