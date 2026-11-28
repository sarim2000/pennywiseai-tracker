package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.BudgetDao
import com.pennywiseai.tracker.data.database.entity.BudgetCategoryEntity
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val budgetDao: BudgetDao
) {
    fun getActiveBudgets(): Flow<List<BudgetEntity>> =
        budgetDao.getActiveBudgets()

    fun getCategoriesForBudget(budgetId: Long): Flow<List<BudgetCategoryEntity>> =
        budgetDao.getCategoriesForBudget(budgetId)
}
