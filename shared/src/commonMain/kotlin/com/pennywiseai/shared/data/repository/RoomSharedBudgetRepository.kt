package com.pennywiseai.shared.data.repository

import com.pennywiseai.shared.data.local.dao.SharedBudgetDao
import com.pennywiseai.shared.data.local.dao.SharedCategoryBudgetLimitDao
import com.pennywiseai.shared.data.local.entity.SharedBudgetCategoryEntity
import com.pennywiseai.shared.data.local.entity.SharedBudgetEntity
import com.pennywiseai.shared.data.local.entity.SharedCategoryBudgetLimitEntity
import kotlinx.coroutines.flow.Flow

class RoomSharedBudgetRepository(
    private val budgetDao: SharedBudgetDao,
    private val limitDao: SharedCategoryBudgetLimitDao
) : SharedBudgetRepository {
    override fun observeBudgets(): Flow<List<SharedBudgetEntity>> = budgetDao.observeAllBudgets()

    override fun observeBudgetCategories(budgetId: Long): Flow<List<SharedBudgetCategoryEntity>> =
        budgetDao.observeCategoriesForBudget(budgetId)

    override fun observeCategoryLimits(): Flow<List<SharedCategoryBudgetLimitEntity>> = limitDao.observeAll()

    override suspend fun upsertBudget(budget: SharedBudgetEntity): Long = budgetDao.upsertBudget(budget)

    override suspend fun replaceBudgetCategories(budgetId: Long, categories: List<SharedBudgetCategoryEntity>) {
        budgetDao.deleteBudgetCategories(budgetId)
        if (categories.isNotEmpty()) {
            budgetDao.insertBudgetCategories(categories.map { it.copy(budgetId = budgetId) })
        }
    }

    override suspend fun upsertCategoryLimit(limit: SharedCategoryBudgetLimitEntity) {
        limitDao.upsert(limit)
    }
}
