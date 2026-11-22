package com.pennywiseai.tracker.data.database.dao

import androidx.room3.*
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionSplitDao {

    @Query("SELECT * FROM transaction_splits WHERE transaction_id = :transactionId ORDER BY id ASC")
    fun getSplitsForTransaction(transactionId: Long): Flow<List<TransactionSplitEntity>>

    @Query("SELECT * FROM transaction_splits ORDER BY id ASC")
    fun getAllSplits(): Flow<List<TransactionSplitEntity>>

    /**
     * Splits of every non-deleted transaction, observed so a filter built on split
     * categories re-runs when a split is edited. Joins rather than taking an id list:
     * `IN (:ids)` binds one variable per id, and SQLite before 3.32 (Android 11 and
     * older) throws "too many SQL variables" past 999.
     */
    @Query("""
        SELECT s.* FROM transaction_splits s
        INNER JOIN transactions t ON t.id = s.transaction_id
        WHERE t.is_deleted = 0
    """)
    fun observeSplitsForActiveTransactions(): Flow<List<TransactionSplitEntity>>

    @Transaction
    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND date_time >= :startDate
        AND date_time <= :endDate
        AND currency = :currency
        ORDER BY date_time DESC
    """)
    fun getTransactionsWithSplitsFiltered(
        startDate: java.time.LocalDateTime,
        endDate: java.time.LocalDateTime,
        currency: String
    ): Flow<List<TransactionWithSplits>>

    @Transaction
    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND date_time >= :startDate
        AND date_time <= :endDate
        ORDER BY date_time DESC
    """)
    fun getTransactionsWithSplitsAllCurrencies(
        startDate: java.time.LocalDateTime,
        endDate: java.time.LocalDateTime
    ): Flow<List<TransactionWithSplits>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSplit(split: TransactionSplitEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSplits(splits: List<TransactionSplitEntity>)

    @Query("DELETE FROM transaction_splits WHERE transaction_id = :transactionId")
    suspend fun deleteSplitsForTransaction(transactionId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM transaction_splits WHERE transaction_id = :transactionId LIMIT 1)")
    suspend fun hasSplits(transactionId: Long): Boolean
}
