package com.pennywiseai.tracker.data.database.dao

import androidx.room.*
import com.pennywiseai.tracker.data.database.entity.BudgetCategoryEntity
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    
    @Query("SELECT * FROM categories ORDER BY display_order ASC, name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>
    
    @Query("SELECT * FROM categories WHERE is_income = 0 ORDER BY display_order ASC, name ASC")
    fun getExpenseCategories(): Flow<List<CategoryEntity>>
    
    @Query("SELECT * FROM categories WHERE is_income = 1 ORDER BY display_order ASC, name ASC")
    fun getIncomeCategories(): Flow<List<CategoryEntity>>

    // Visible-only variants for the category PICKERS — hidden categories are kept
    // in the DB (so existing transactions keep their category and still show in
    // analytics) but excluded from selection (#736).
    @Query("SELECT * FROM categories WHERE is_hidden = 0 ORDER BY display_order ASC, name ASC")
    fun getVisibleCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE is_income = 0 AND is_hidden = 0 ORDER BY display_order ASC, name ASC")
    fun getVisibleExpenseCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE is_income = 1 AND is_hidden = 0 ORDER BY display_order ASC, name ASC")
    fun getVisibleIncomeCategories(): Flow<List<CategoryEntity>>

    @Query("UPDATE categories SET is_hidden = :hidden WHERE id = :categoryId")
    suspend fun setCategoryHidden(categoryId: Long, hidden: Boolean)

    /**
     * Flips the flag in the database rather than writing a value the caller
     * worked out beforehand.
     *
     * The UI can only ever hold a snapshot of the row: between a write landing
     * and the Room Flow reaching Compose, a second tap would compute its "next"
     * value from the pre-write state and write the same thing again, so a
     * quick hide-then-show left the category hidden. Flipping in SQL has no such
     * window.
     */
    @Query("UPDATE categories SET is_hidden = NOT is_hidden WHERE id = :categoryId")
    suspend fun toggleCategoryHidden(categoryId: Long)

    @Query("SELECT * FROM categories WHERE id = :categoryId")
    suspend fun getCategoryById(categoryId: Long): CategoryEntity?

    @Query("SELECT * FROM categories ORDER BY display_order ASC, name ASC")
    suspend fun getAllCategoriesList(): List<CategoryEntity>

    @Query("UPDATE categories SET is_hidden = :hidden WHERE parent_id = :parentId")
    suspend fun setChildrenHidden(parentId: Long, hidden: Boolean)

    /**
     * Flips a category's hidden flag and cascades in one transaction (#374):
     * a hidden parent hides its children; un-hiding a child restores its
     * parent — so the hierarchy can never be committed half-way.
     */
    @Transaction
    suspend fun toggleCategoryHiddenCascading(categoryId: Long): CategoryEntity? {
        toggleCategoryHidden(categoryId)
        val updated = getCategoryById(categoryId) ?: return null
        setChildrenHidden(categoryId, updated.isHidden)
        if (!updated.isHidden) updated.parentId?.let { setCategoryHidden(it, false) }
        return updated
    }

    /** Deleting a parent promotes its children to top level (#374). */
    @Query("UPDATE categories SET parent_id = NULL WHERE parent_id = :parentId")
    suspend fun detachChildren(parentId: Long)
    
    @Query("SELECT * FROM categories WHERE name = :categoryName LIMIT 1")
    suspend fun getCategoryByName(categoryName: String): CategoryEntity?
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: CategoryEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<CategoryEntity>)
    
    @Update
    suspend fun updateCategory(category: CategoryEntity)
    
    @Query("DELETE FROM categories WHERE id = :categoryId AND is_system = 0")
    suspend fun deleteCategory(categoryId: Long)
    
    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoryCount(): Int
    
    @Query("SELECT EXISTS(SELECT 1 FROM categories WHERE name = :categoryName)")
    suspend fun categoryExists(categoryName: String): Boolean
    
    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    // --- Rename (#823) ------------------------------------------------------
    // Seven tables store a category by *name*, not id, so renaming only the
    // categories row left every existing reference pointing at a name that no
    // longer exists. Budget rows are only renamed when they track a category
    // (match_type IS NULL); otherwise category_name is just a type's label.

    @Query("UPDATE transactions SET category = :newName, updated_at = strftime('%Y-%m-%dT%H:%M:%f', 'now', 'localtime') WHERE category = :oldName")
    suspend fun renameInTransactions(oldName: String, newName: String)

    @Query("UPDATE transactions SET budget_category = :newName, updated_at = strftime('%Y-%m-%dT%H:%M:%f', 'now', 'localtime') WHERE budget_category = :oldName")
    suspend fun renameInTransactionBudgetCategory(oldName: String, newName: String)

    @Query("UPDATE transaction_splits SET category = :newName WHERE category = :oldName")
    suspend fun renameInSplits(oldName: String, newName: String)

    @Query("UPDATE budget_categories SET category_name = :newName WHERE category_name = :oldName AND match_type IS NULL")
    suspend fun renameInBudgetCategories(oldName: String, newName: String)

    @Query("SELECT * FROM budget_categories WHERE match_type IS NULL AND category_name IN (:oldName, :newName)")
    suspend fun budgetAllocationsNamed(oldName: String, newName: String): List<BudgetCategoryEntity>

    @Query("UPDATE budget_categories SET budget_amount = :amount WHERE id = :id")
    suspend fun setBudgetAllocationAmount(id: Long, amount: java.math.BigDecimal)

    @Query("DELETE FROM budget_categories WHERE id = :id")
    suspend fun deleteBudgetAllocation(id: Long)

    @Query("UPDATE budget_category_month_snapshots SET category_name = :newName WHERE category_name = :oldName AND match_type IS NULL")
    suspend fun renameInBudgetSnapshots(oldName: String, newName: String)

    @Query("UPDATE merchant_mappings SET category = :newName WHERE category = :oldName")
    suspend fun renameInMerchantMappings(oldName: String, newName: String)

    @Query("UPDATE recurring_transactions SET category = :newName WHERE category = :oldName")
    suspend fun renameInRecurring(oldName: String, newName: String)

    @Query("UPDATE subscriptions SET category = :newName WHERE category = :oldName")
    suspend fun renameInSubscriptions(oldName: String, newName: String)

    /** Points every stored reference to [oldName] at [newName]. Call inside a transaction. */
    suspend fun renameReferences(oldName: String, newName: String) {
        renameInTransactions(oldName, newName)
        renameInTransactionBudgetCategory(oldName, newName)
        renameInSplits(oldName, newName)
        // A budget already holding newName would hit the unique index: fold the
        // old row's amount into it first so no allocation is lost.
        allocationMergesForRename(budgetAllocationsNamed(oldName, newName), oldName, newName).forEach {
            setBudgetAllocationAmount(it.keepId, it.amount)
            deleteBudgetAllocation(it.dropId)
        }
        renameInBudgetCategories(oldName, newName)
        renameInBudgetSnapshots(oldName, newName)
        renameInMerchantMappings(oldName, newName)
        renameInRecurring(oldName, newName)
        renameInSubscriptions(oldName, newName)
    }
}
