package com.pennywiseai.tracker.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pennywiseai.tracker.data.database.entity.CustomParserRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomParserRuleDao {

    @Query("SELECT * FROM custom_parser_rules ORDER BY priority ASC, name ASC")
    fun getAllRules(): Flow<List<CustomParserRuleEntity>>

    @Query("SELECT * FROM custom_parser_rules WHERE is_active = 1 ORDER BY priority ASC")
    suspend fun getActiveRules(): List<CustomParserRuleEntity>

    @Query("SELECT * FROM custom_parser_rules WHERE id = :id")
    suspend fun getRuleById(id: Long): CustomParserRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: CustomParserRuleEntity): Long

    @Update
    suspend fun updateRule(rule: CustomParserRuleEntity)

    @Query("UPDATE custom_parser_rules SET is_active = :isActive, updated_at = :updatedAt WHERE id = :id")
    suspend fun setActive(id: Long, isActive: Boolean, updatedAt: java.time.LocalDateTime = java.time.LocalDateTime.now())

    @Delete
    suspend fun deleteRule(rule: CustomParserRuleEntity)

    @Query("DELETE FROM custom_parser_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)
}
