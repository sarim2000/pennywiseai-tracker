package com.pennywiseai.shared.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pennywiseai.shared.data.local.entity.SharedCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedCategoryDao {
    @Query("SELECT * FROM shared_categories ORDER BY display_order ASC, name ASC")
    fun observeCategories(): Flow<List<SharedCategoryEntity>>

    @Query("SELECT * FROM shared_categories WHERE is_income = :isIncome ORDER BY display_order ASC, name ASC")
    fun observeCategoriesByIncomeType(isIncome: Boolean): Flow<List<SharedCategoryEntity>>

    @Query("SELECT COUNT(*) FROM shared_categories")
    suspend fun countCategories(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<SharedCategoryEntity>)
}
