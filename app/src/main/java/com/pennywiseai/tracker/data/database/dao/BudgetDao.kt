package com.pennywiseai.tracker.data.database.dao

import androidx.room.*
import com.pennywiseai.tracker.data.database.entity.BudgetCategoryEntity
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets WHERE is_active = 1 ORDER BY created_at DESC")
    fun getActiveBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets ORDER BY created_at DESC")
    fun getAllBudgets(): Flow<List<BudgetEntity>>

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

    @Query("SELECT * FROM budgets WHERE name = :name AND is_active = 1 LIMIT 1")
    suspend fun getActiveBudgetByName(name: String): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity): Long

    @Update
    suspend fun updateBudget(budget: BudgetEntity)

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

    @Query("DELETE FROM budget_categories WHERE id = :categoryId")
    suspend fun deleteBudgetCategoryById(categoryId: Long)

    // Get budgets that include a specific category (for spending calculation)
    @Query("""
        SELECT b.* FROM budgets b
        INNER JOIN budget_categories bc ON b.id = bc.budget_id
        WHERE bc.category_name = :categoryName
        AND b.is_active = 1
        AND :today >= b.start_date
        AND :today <= b.end_date
    """)
    suspend fun getBudgetsForCategory(categoryName: String, today: LocalDate): List<BudgetEntity>

    // Get budgets that include all categories
    @Query("""
        SELECT * FROM budgets
        WHERE include_all_categories = 1
        AND is_active = 1
        AND :today >= start_date
        AND :today <= end_date
    """)
    suspend fun getBudgetsWithAllCategories(today: LocalDate): List<BudgetEntity>

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

    @Query("""
        SELECT b.* FROM budgets b
        INNER JOIN budget_categories bc ON b.id = bc.budget_id
        WHERE b.is_active = 1 AND bc.category_name = :categoryName
        LIMIT 1
    """)
    suspend fun getActiveBudgetForCategory(categoryName: String): BudgetEntity?

    @Query("UPDATE budget_categories SET budget_amount = :amount WHERE budget_id = :budgetId AND category_name = :categoryName")
    suspend fun updateCategoryBudgetAmount(budgetId: Long, categoryName: String, amount: java.math.BigDecimal)

    @Query("DELETE FROM budget_categories WHERE budget_id = :budgetId AND category_name = :categoryName")
    suspend fun deleteCategoryFromBudget(budgetId: Long, categoryName: String)

    @Query("UPDATE budgets SET limit_amount = :amount, updated_at = datetime('now') WHERE id = :budgetId")
    suspend fun updateBudgetLimitAmount(budgetId: Long, amount: java.math.BigDecimal)

    @Query("SELECT COUNT(*) FROM budgets WHERE is_active = 1")
    suspend fun getActiveGroupCount(): Int
}
