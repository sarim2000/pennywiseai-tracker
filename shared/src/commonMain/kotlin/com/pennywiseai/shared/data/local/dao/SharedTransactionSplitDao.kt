package com.pennywiseai.shared.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pennywiseai.shared.data.local.entity.SharedTransactionSplitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedTransactionSplitDao {
    @Query("SELECT * FROM shared_transaction_splits WHERE transaction_id = :transactionId ORDER BY id")
    fun observeForTransaction(transactionId: Long): Flow<List<SharedTransactionSplitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(splits: List<SharedTransactionSplitEntity>)

    @Query("DELETE FROM shared_transaction_splits WHERE transaction_id = :transactionId")
    suspend fun deleteForTransaction(transactionId: Long)
}
