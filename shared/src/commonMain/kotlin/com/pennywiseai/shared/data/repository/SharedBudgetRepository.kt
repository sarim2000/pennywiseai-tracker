package com.pennywiseai.shared.data.repository

import com.pennywiseai.shared.data.local.entity.SharedBudgetCategoryEntity
import com.pennywiseai.shared.data.local.entity.SharedBudgetEntity
import com.pennywiseai.shared.data.local.entity.SharedCategoryBudgetLimitEntity
import kotlinx.coroutines.flow.Flow

interface SharedBudgetRepository {
    fun observeBudgets(): Flow<List<SharedBudgetEntity>>
    fun observeBudgetCategories(budgetId: Long): Flow<List<SharedBudgetCategoryEntity>>
    fun observeCategoryLimits(): Flow<List<SharedCategoryBudgetLimitEntity>>
    suspend fun upsertBudget(budget: SharedBudgetEntity): Long
    suspend fun replaceBudgetCategories(budgetId: Long, categories: List<SharedBudgetCategoryEntity>)
    suspend fun upsertCategoryLimit(limit: SharedCategoryBudgetLimitEntity)
}
