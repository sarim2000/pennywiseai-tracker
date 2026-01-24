package com.pennywiseai.tracker.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pennywiseai.tracker.data.database.entity.CategoryBudgetLimitEntity
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

@Dao
interface CategoryBudgetLimitDao {

    @Query("SELECT * FROM category_budget_limits ORDER BY category_name ASC")
    fun getAllLimits(): Flow<List<CategoryBudgetLimitEntity>>

    @Query("SELECT * FROM category_budget_limits WHERE category_name = :categoryName")
    suspend fun getLimitForCategory(categoryName: String): CategoryBudgetLimitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLimit(limit: CategoryBudgetLimitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLimits(limits: List<CategoryBudgetLimitEntity>)

    @Query("DELETE FROM category_budget_limits WHERE category_name = :categoryName")
    suspend fun deleteLimitForCategory(categoryName: String)

    @Query("DELETE FROM category_budget_limits")
    suspend fun deleteAllLimits()
}
