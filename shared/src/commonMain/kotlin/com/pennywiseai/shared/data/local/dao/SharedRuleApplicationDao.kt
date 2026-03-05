package com.pennywiseai.shared.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pennywiseai.shared.data.local.entity.SharedRuleApplicationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedRuleApplicationDao {
    @Query("SELECT * FROM shared_rule_applications ORDER BY applied_at_epoch_millis DESC")
    fun observeRecent(): Flow<List<SharedRuleApplicationEntity>>

    @Query("SELECT * FROM shared_rule_applications WHERE transaction_id = :transactionId")
    suspend fun getByTransaction(transactionId: Long): List<SharedRuleApplicationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(application: SharedRuleApplicationEntity)

    @Query("DELETE FROM shared_rule_applications WHERE transaction_id = :transactionId")
    suspend fun deleteByTransaction(transactionId: Long)
}
