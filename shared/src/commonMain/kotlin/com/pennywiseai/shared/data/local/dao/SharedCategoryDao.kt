package com.pennywiseai.shared.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pennywiseai.shared.data.local.entity.SharedCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedCategoryDao {
    @Query("SELECT * FROM shared_categories ORDER BY display_order ASC, name ASC")
    fun observeCategories(): Flow<List<SharedCategoryEntity>>

    @Query("SELECT * FROM shared_categories WHERE is_income = :isIncome ORDER BY display_order ASC, name ASC")
    fun observeCategoriesByIncomeType(isIncome: Boolean): Flow<List<SharedCategoryEntity>>

    @Query("SELECT * FROM shared_categories WHERE id = :id")
    suspend fun getById(id: Long): SharedCategoryEntity?

    @Query("SELECT COUNT(*) FROM shared_categories")
    suspend fun countCategories(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<SharedCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: SharedCategoryEntity): Long

    @Update
    suspend fun update(category: SharedCategoryEntity)

    @Query("DELETE FROM shared_categories WHERE id = :id AND is_system = 0")
    suspend fun deleteNonSystem(id: Long): Int
}
