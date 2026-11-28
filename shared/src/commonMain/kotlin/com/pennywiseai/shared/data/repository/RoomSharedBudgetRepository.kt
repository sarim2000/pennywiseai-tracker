package com.pennywiseai.shared.data.repository

import com.pennywiseai.shared.data.local.dao.SharedBudgetDao
import com.pennywiseai.shared.data.local.entity.SharedBudgetCategoryEntity
import com.pennywiseai.shared.data.local.entity.SharedBudgetEntity
import kotlinx.coroutines.flow.Flow

class RoomSharedBudgetRepository(
    private val budgetDao: SharedBudgetDao
) : SharedBudgetRepository {
    override fun observeBudgets(): Flow<List<SharedBudgetEntity>> = budgetDao.observeAllBudgets()

    override fun observeBudgetCategories(budgetId: Long): Flow<List<SharedBudgetCategoryEntity>> =
        budgetDao.observeCategoriesForBudget(budgetId)

    override suspend fun upsertBudget(budget: SharedBudgetEntity): Long = budgetDao.upsertBudget(budget)

    override suspend fun deleteBudget(id: Long) {
        budgetDao.deleteBudgetCategories(id)
        budgetDao.deleteBudget(id)
    }

    override suspend fun replaceBudgetCategories(budgetId: Long, categories: List<SharedBudgetCategoryEntity>) {
        budgetDao.deleteBudgetCategories(budgetId)
        if (categories.isNotEmpty()) {
            budgetDao.insertBudgetCategories(categories.map { it.copy(budgetId = budgetId) })
        }
    }
}
