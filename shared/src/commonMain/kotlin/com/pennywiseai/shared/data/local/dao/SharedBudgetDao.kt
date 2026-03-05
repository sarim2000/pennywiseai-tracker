package com.pennywiseai.shared.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pennywiseai.shared.data.local.entity.SharedBudgetCategoryEntity
import com.pennywiseai.shared.data.local.entity.SharedBudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedBudgetDao {
    @Query("SELECT * FROM shared_budgets ORDER BY updated_at_epoch_millis DESC")
    fun observeAllBudgets(): Flow<List<SharedBudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: SharedBudgetEntity): Long

    @Update
    suspend fun updateBudget(budget: SharedBudgetEntity)

    @Query("DELETE FROM shared_budgets WHERE id = :id")
    suspend fun deleteBudget(id: Long)

    @Query("SELECT * FROM shared_budget_categories WHERE budget_id = :budgetId")
    fun observeCategoriesForBudget(budgetId: Long): Flow<List<SharedBudgetCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgetCategories(categories: List<SharedBudgetCategoryEntity>)

    @Query("DELETE FROM shared_budget_categories WHERE budget_id = :budgetId")
    suspend fun deleteBudgetCategories(budgetId: Long)
}
