package com.pennywiseai.shared.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pennywiseai.shared.data.local.entity.SharedCategoryBudgetLimitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedCategoryBudgetLimitDao {
    @Query("SELECT * FROM shared_category_budget_limits ORDER BY category_name")
    fun observeAll(): Flow<List<SharedCategoryBudgetLimitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(limit: SharedCategoryBudgetLimitEntity)

    @Query("DELETE FROM shared_category_budget_limits WHERE category_name = :categoryName")
    suspend fun deleteByCategory(categoryName: String)
}
