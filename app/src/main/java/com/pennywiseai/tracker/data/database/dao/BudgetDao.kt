package com.pennywiseai.tracker.data.database.dao

import androidx.room3.*
import com.pennywiseai.tracker.data.database.entity.BudgetCategoryEntity
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets WHERE is_active = 1 ORDER BY display_order ASC")
    fun getActiveBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets ORDER BY created_at DESC")
    fun getAllBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budget_categories ORDER BY budget_id")
    fun getAllBudgetCategories(): Flow<List<BudgetCategoryEntity>>

    @Query("""
        SELECT * FROM budgets
        WHERE is_active = 1
        AND :today >= start_date
        AND :today <= end_date
        ORDER BY created_at DESC
    """)
    fun getCurrentBudgets(today: LocalDate): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :budgetId")
    suspend fun getBudgetById(budgetId: Long): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE id = :budgetId")
    fun getBudgetByIdFlow(budgetId: Long): Flow<BudgetEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity): Long

    @Update
    suspend fun updateBudget(budget: BudgetEntity)

    @Update
    suspend fun updateBudgets(budgets: List<BudgetEntity>)

    @Update
    suspend fun updateBudgetCategories(categories: List<BudgetCategoryEntity>)

    @Transaction
    suspend fun updateBudgetsAndCategories(budgets: List<BudgetEntity>, categories: List<BudgetCategoryEntity>) {
        updateBudgets(budgets)
        updateBudgetCategories(categories)
    }

    @Query("UPDATE budgets SET is_active = 0, updated_at = datetime('now') WHERE id = :budgetId")
    suspend fun deactivateBudget(budgetId: Long)

    @Delete
    suspend fun deleteBudget(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :budgetId")
    suspend fun deleteBudgetById(budgetId: Long)

    // Budget Categories
    @Query("SELECT * FROM budget_categories WHERE budget_id = :budgetId")
    fun getCategoriesForBudget(budgetId: Long): Flow<List<BudgetCategoryEntity>>

    @Query("SELECT * FROM budget_categories WHERE budget_id = :budgetId")
    suspend fun getCategoriesForBudgetList(budgetId: Long): List<BudgetCategoryEntity>

    @Query("SELECT category_name FROM budget_categories WHERE budget_id = :budgetId")
    suspend fun getCategoryNamesForBudget(budgetId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgetCategory(category: BudgetCategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgetCategories(categories: List<BudgetCategoryEntity>)

    @Query("DELETE FROM budget_categories WHERE budget_id = :budgetId")
    suspend fun deleteCategoriesForBudget(budgetId: Long)

    @Query("DELETE FROM budgets")
    suspend fun deleteAllBudgets()

    // Budget Groups queries
    @Transaction
    @Query("SELECT * FROM budgets WHERE is_active = 1 ORDER BY display_order ASC, created_at DESC")
    fun getActiveBudgetsWithCategories(): Flow<List<BudgetWithCategories>>

    @Query("""
        SELECT DISTINCT bc.category_name FROM budget_categories bc
        INNER JOIN budgets b ON bc.budget_id = b.id
        WHERE b.is_active = 1
    """)
    fun getAllAssignedCategoryNames(): Flow<List<String>>

    @Query("UPDATE budget_categories SET budget_amount = :amount WHERE budget_id = :budgetId AND category_name = :categoryName")
    suspend fun updateCategoryBudgetAmount(budgetId: Long, categoryName: String, amount: java.math.BigDecimal)

    @Query("DELETE FROM budget_categories WHERE budget_id = :budgetId AND category_name = :categoryName")
    suspend fun deleteCategoryFromBudget(budgetId: Long, categoryName: String)

    @Query("UPDATE budgets SET limit_amount = :amount, updated_at = datetime('now') WHERE id = :budgetId")
    suspend fun updateBudgetLimitAmount(budgetId: Long, amount: java.math.BigDecimal)

    /** Only the rollover start, so a row read earlier can't undo another save's other columns. */
    @Query("UPDATE budgets SET rollover_since = :since WHERE id = :budgetId")
    suspend fun updateRolloverSince(budgetId: Long, since: LocalDate?)

    @Query("SELECT COUNT(*) FROM budgets WHERE is_active = 1")
    suspend fun getActiveGroupCount(): Int

    @Query("SELECT COALESCE(MAX(display_order), -1) FROM budgets WHERE is_active = 1")
    suspend fun getMaxDisplayOrder(): Int
}
