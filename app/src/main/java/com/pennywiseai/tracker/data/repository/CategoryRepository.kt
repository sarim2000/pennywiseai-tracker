package com.pennywiseai.tracker.data.repository

import com.pennywiseai.shared.data.bootstrap.DefaultCategoryData
import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.CategoryDao
import com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.hierarchical
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val database: PennyWiseDatabase,
    private val ruleRepository: RuleRepository
) {
    
    fun getAllCategories(): Flow<List<CategoryEntity>> {
        return categoryDao.getAllCategories().map { it.hierarchical() }
    }
    
    fun getExpenseCategories(): Flow<List<CategoryEntity>> {
        return categoryDao.getExpenseCategories().map { it.hierarchical() }
    }
    
    fun getIncomeCategories(): Flow<List<CategoryEntity>> {
        return categoryDao.getIncomeCategories().map { it.hierarchical() }
    }

    // Visible-only variants for pickers — hidden categories are excluded (#736).
    fun getVisibleCategories(): Flow<List<CategoryEntity>> {
        return categoryDao.getVisibleCategories().map { it.hierarchical() }
    }

    fun getVisibleExpenseCategories(): Flow<List<CategoryEntity>> {
        return categoryDao.getVisibleExpenseCategories().map { it.hierarchical() }
    }

    fun getVisibleIncomeCategories(): Flow<List<CategoryEntity>> {
        return categoryDao.getVisibleIncomeCategories().map { it.hierarchical() }
    }

    suspend fun setCategoryHidden(categoryId: Long, hidden: Boolean) {
        categoryDao.setCategoryHidden(categoryId, hidden)
    }

    /**
     * Flips a category's hidden flag and returns the row as it now stands.
     * See [CategoryDao.toggleCategoryHidden] for why this isn't a read,
     * flip and write from the caller.
     */
    suspend fun toggleCategoryHidden(categoryId: Long): CategoryEntity? =
        categoryDao.toggleCategoryHiddenCascading(categoryId)

    suspend fun getCategoryById(categoryId: Long): CategoryEntity? {
        return categoryDao.getCategoryById(categoryId)
    }
    
    suspend fun getCategoryByName(categoryName: String): CategoryEntity? {
        return categoryDao.getCategoryByName(categoryName)
    }
    
    suspend fun createCategory(
        name: String,
        color: String,
        isIncome: Boolean = false,
        icon: String? = null,
        parentId: Long? = null
    ): Long {
        val category = CategoryEntity(
            name = name,
            color = color,
            icon = icon,
            parentId = parentId,
            isSystem = false,
            isIncome = isIncome,
            displayOrder = 999
        )
        return categoryDao.insertCategory(category)
    }
    
    /**
     * Saves an edited category. When its name changed, every transaction,
     * split, budget, merchant mapping, recurring entry, subscription and rule
     * that stored [previousName] moves to the new name in the same transaction,
     * so nothing is left pointing at a name that no longer exists.
     */
    suspend fun updateCategory(category: CategoryEntity, previousName: String = category.name) {
        // The built-in name mirror switches inside the transaction, before commit, so
        // nothing classified after this rename can still get the old name (#823); a
        // row saved under the new name before commit waits on the write lock and
        // finds it once the rename lands. A failed rename puts the mirror back.
        val mirrorBefore = BuiltinCategoryNames.snapshot()
        try {
            database.withTransaction {
                categoryDao.updateCategory(category.copy(updatedAt = LocalDateTime.now()))
                if (previousName != category.name) {
                    categoryDao.renameReferences(previousName, category.name)
                    ruleRepository.renameCategory(previousName, category.name)
                    BuiltinCategoryNames.update(categoryDao.getAllCategoriesList())
                }
            }
        } catch (e: Exception) {
            BuiltinCategoryNames.restore(mirrorBefore)
            throw e
        }
    }
    
    suspend fun deleteCategory(categoryId: Long): Boolean {
        // Only delete non-system categories
        val category = categoryDao.getCategoryById(categoryId)
        if (category != null && !category.isSystem) {
            categoryDao.detachChildren(categoryId)
            categoryDao.deleteCategory(categoryId)
            return true
        }
        return false
    }
    
    suspend fun categoryExists(categoryName: String): Boolean {
        return categoryDao.categoryExists(categoryName)
    }
    
    suspend fun initializeDefaultCategories() {
        // Only initialize if no categories exist
        if (categoryDao.getCategoryCount() == 0) {
            val defaultCategories = DefaultCategoryData.ALL.map { seed ->
                CategoryEntity(
                    name = seed.name,
                    color = seed.colorHex,
                    isSystem = true,
                    systemName = seed.name,
                    isIncome = seed.isIncome,
                    displayOrder = DefaultCategoryData.ALL.indexOf(seed) + 1
                )
            }
            categoryDao.insertCategories(defaultCategories)
        }
    }
}