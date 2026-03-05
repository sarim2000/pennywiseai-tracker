package com.pennywiseai.shared.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pennywiseai.shared.data.local.entity.SharedTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedTransactionDao {
    @Query("SELECT * FROM shared_transactions WHERE is_deleted = 0 ORDER BY occurred_at_epoch_millis DESC")
    fun observeTransactions(): Flow<List<SharedTransactionEntity>>

    @Query("SELECT * FROM shared_transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SharedTransactionEntity?

    @Query("SELECT * FROM shared_transactions WHERE transaction_hash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): SharedTransactionEntity?

    @Query("SELECT * FROM shared_transactions WHERE reference = :reference LIMIT 1")
    suspend fun getByReference(reference: String): SharedTransactionEntity?

    @Query("""
        SELECT * FROM shared_transactions
        WHERE amount_minor = :amountMinor
          AND occurred_at_epoch_millis BETWEEN :startEpochMillis AND :endEpochMillis
          AND is_deleted = 0
    """)
    suspend fun getByAmountAndDate(
        amountMinor: Long,
        startEpochMillis: Long,
        endEpochMillis: Long
    ): List<SharedTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: SharedTransactionEntity): Long

    @Update
    suspend fun update(transaction: SharedTransactionEntity)

    @Query("UPDATE shared_transactions SET is_deleted = 1, updated_at_epoch_millis = :updatedAtEpochMillis WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAtEpochMillis: Long)

    @Query("DELETE FROM shared_transactions")
    suspend fun deleteAll()
}
