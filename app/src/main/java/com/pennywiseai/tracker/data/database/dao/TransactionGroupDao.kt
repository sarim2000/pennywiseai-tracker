package com.pennywiseai.tracker.data.database.dao

import androidx.room.*
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionGroupDao {

    @Insert
    suspend fun insertGroup(group: TransactionGroupEntity): Long

    @Update
    suspend fun updateGroup(group: TransactionGroupEntity)

    @Delete
    suspend fun deleteGroup(group: TransactionGroupEntity)

    @Transaction
    suspend fun unlinkAndDeleteGroup(group: TransactionGroupEntity) {
        unlinkAllTransactions(group.id)
        deleteGroup(group)
    }

    @Query("SELECT * FROM transaction_groups WHERE id = :id")
    suspend fun getGroupById(id: Long): TransactionGroupEntity?

    @Query("SELECT * FROM transaction_groups ORDER BY updated_at DESC")
    fun getAllGroups(): Flow<List<TransactionGroupEntity>>

    @Query("SELECT * FROM transactions WHERE group_id = :groupId AND is_deleted = 0 ORDER BY date_time DESC")
    fun getTransactionsForGroup(groupId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE group_id = :groupId AND is_deleted = 0")
    fun getTransactionCount(groupId: Long): Flow<Int>

    @Query("UPDATE transactions SET group_id = :groupId WHERE id = :transactionId")
    suspend fun linkTransaction(transactionId: Long, groupId: Long)

    @Query("UPDATE transactions SET group_id = NULL WHERE id = :transactionId")
    suspend fun unlinkTransaction(transactionId: Long)

    @Query("UPDATE transactions SET group_id = NULL WHERE group_id = :groupId")
    suspend fun unlinkAllTransactions(groupId: Long)

    @Query("""
        SELECT * FROM transactions
        WHERE group_id IS NULL AND is_deleted = 0
        ORDER BY date_time DESC
        LIMIT :limit
    """)
    fun getRecentUngroupedTransactions(limit: Int = 50): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions
        WHERE group_id IS NULL AND is_deleted = 0
        AND (merchant_name LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%')
        ORDER BY date_time DESC
        LIMIT :limit
    """)
    fun searchUngroupedTransactions(query: String, limit: Int = 50): Flow<List<TransactionEntity>>
}
