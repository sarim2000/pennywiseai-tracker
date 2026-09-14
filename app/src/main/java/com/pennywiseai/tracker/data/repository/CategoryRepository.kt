package com.pennywiseai.tracker.data.repository

import com.pennywiseai.shared.data.bootstrap.DefaultCategoryData
import com.pennywiseai.tracker.data.database.dao.CategoryDao
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.hierarchical
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao
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
    suspend fun toggleCategoryHidden(categoryId: Long): CategoryEntity? {
        categoryDao.toggleCategoryHidden(categoryId)
        val updated = categoryDao.getCategoryById(categoryId) ?: return null
        // Keep the hierarchy consistent (#374): a hidden parent hides its
        // children; un-hiding a child brings its parent back so it never
        // renders as an indented orphan.
        categoryDao.setChildrenHidden(categoryId, updated.isHidden)
        if (!updated.isHidden) updated.parentId?.let { categoryDao.setCategoryHidden(it, false) }
        return updated
    }

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
    
    suspend fun updateCategory(category: CategoryEntity) {
        categoryDao.updateCategory(
            category.copy(updatedAt = LocalDateTime.now())
        )
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
                    isIncome = seed.isIncome,
                    displayOrder = DefaultCategoryData.ALL.indexOf(seed) + 1
                )
            }
            categoryDao.insertCategories(defaultCategories)
        }
    }
}