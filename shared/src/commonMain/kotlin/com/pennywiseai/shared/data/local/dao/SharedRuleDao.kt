package com.pennywiseai.shared.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pennywiseai.shared.data.local.entity.SharedRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedRuleDao {
    @Query("SELECT * FROM shared_transaction_rules ORDER BY priority ASC")
    fun observeAll(): Flow<List<SharedRuleEntity>>

    @Query("SELECT * FROM shared_transaction_rules WHERE is_active = 1 ORDER BY priority ASC")
    suspend fun getActive(): List<SharedRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: SharedRuleEntity)

    @Update
    suspend fun update(rule: SharedRuleEntity)

    @Query("DELETE FROM shared_transaction_rules WHERE id = :ruleId")
    suspend fun deleteById(ruleId: String)
}
