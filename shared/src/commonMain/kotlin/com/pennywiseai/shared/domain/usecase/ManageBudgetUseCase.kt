package com.pennywiseai.shared.domain.usecase

import com.pennywiseai.shared.data.local.entity.SharedBudgetCategoryEntity
import com.pennywiseai.shared.data.local.entity.SharedBudgetEntity
import com.pennywiseai.shared.data.local.entity.SharedCategoryBudgetLimitEntity
import com.pennywiseai.shared.data.repository.SharedBudgetRepository
import com.pennywiseai.shared.data.util.currentTimeMillis

class ManageBudgetUseCase(
    private val repository: SharedBudgetRepository
) {
    suspend fun upsertBudget(
        name: String,
        limitMinor: Long,
        periodType: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
        currency: String = "INR"
    ): Long {
        val now = currentTimeMillis()
        return repository.upsertBudget(
            SharedBudgetEntity(
                name = name.trim(),
                limitMinor = limitMinor,
                periodType = periodType,
                startEpochMillis = startEpochMillis,
                endEpochMillis = endEpochMillis,
                groupType = "GENERAL",
                currency = currency,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
        )
    }

    suspend fun setBudgetCategories(budgetId: Long, categories: List<Pair<String, Long>>) {
        repository.replaceBudgetCategories(
            budgetId = budgetId,
            categories = categories.map { (categoryName, amount) ->
                SharedBudgetCategoryEntity(
                    budgetId = budgetId,
                    categoryName = categoryName,
                    budgetAmountMinor = amount
                )
            }
        )
    }

    suspend fun setCategoryLimit(categoryName: String, limitAmountMinor: Long) {
        repository.upsertCategoryLimit(
            SharedCategoryBudgetLimitEntity(
                categoryName = categoryName,
                limitAmountMinor = limitAmountMinor
            )
        )
    }
}
