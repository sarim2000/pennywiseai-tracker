package com.pennywiseai.tracker.data.database.dao

import androidx.room.*
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionSplitDao {

    @Query("SELECT * FROM transaction_splits WHERE transaction_id = :transactionId ORDER BY id ASC")
    fun getSplitsForTransaction(transactionId: Long): Flow<List<TransactionSplitEntity>>

    @Query("SELECT * FROM transaction_splits WHERE transaction_id = :transactionId ORDER BY id ASC")
    suspend fun getSplitsForTransactionSync(transactionId: Long): List<TransactionSplitEntity>

    @Query("SELECT * FROM transaction_splits WHERE transaction_id IN (:transactionIds)")
    suspend fun getSplitsForTransactions(transactionIds: List<Long>): List<TransactionSplitEntity>

    @Transaction
    @Query("SELECT * FROM transactions WHERE id = :transactionId AND is_deleted = 0")
    fun getTransactionWithSplits(transactionId: Long): Flow<TransactionWithSplits?>

    @Transaction
    @Query("SELECT * FROM transactions WHERE id = :transactionId AND is_deleted = 0")
    suspend fun getTransactionWithSplitsSync(transactionId: Long): TransactionWithSplits?

    @Transaction
    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND date_time >= :startDate
        AND date_time <= :endDate
        ORDER BY date_time DESC
    """)
    fun getTransactionsWithSplitsInRange(
        startDate: String,
        endDate: String
    ): Flow<List<TransactionWithSplits>>

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

    @Update
    suspend fun updateSplit(split: TransactionSplitEntity)

    @Delete
    suspend fun deleteSplit(split: TransactionSplitEntity)

    @Query("DELETE FROM transaction_splits WHERE transaction_id = :transactionId")
    suspend fun deleteSplitsForTransaction(transactionId: Long)

    @Query("DELETE FROM transaction_splits WHERE id = :splitId")
    suspend fun deleteSplitById(splitId: Long)

    @Query("SELECT COUNT(*) FROM transaction_splits WHERE transaction_id = :transactionId")
    suspend fun getSplitCountForTransaction(transactionId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM transaction_splits WHERE transaction_id = :transactionId LIMIT 1)")
    suspend fun hasSplits(transactionId: Long): Boolean
}
