package com.pennywiseai.shared.data.repository

import com.pennywiseai.shared.data.model.SharedCategory
import kotlinx.coroutines.flow.Flow

interface SharedCategoryRepository {
    fun observeCategories(): Flow<List<SharedCategory>>
    fun observeCategoriesByIncomeType(isIncome: Boolean): Flow<List<SharedCategory>>
    suspend fun getById(id: Long): SharedCategory?
    suspend fun countCategories(): Int
    suspend fun insertCategories(categories: List<SharedCategory>)
    suspend fun insertCategory(category: SharedCategory): Long
    suspend fun updateCategory(category: SharedCategory)
    suspend fun deleteCategory(id: Long): Boolean
}
