package com.pennywiseai.tracker.data.database.dao

import androidx.room3.*
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import com.pennywiseai.tracker.data.database.entity.TripExclusionEntity
import com.pennywiseai.tracker.data.trip.TripOverlapException
import com.pennywiseai.tracker.data.trip.findOverlappingTrip
import com.pennywiseai.tracker.data.trip.tripExclusionFor
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

    @Transaction
    suspend fun createGroupAndLink(group: TransactionGroupEntity, transactionId: Long): Long {
        val groupId = insertGroup(group)
        linkTransaction(transactionId, groupId)
        return groupId
    }

    @Query("SELECT * FROM transaction_groups WHERE id = :id")
    suspend fun getGroupById(id: Long): TransactionGroupEntity?

    @Query("SELECT * FROM transaction_groups ORDER BY updated_at DESC")
    fun getAllGroups(): Flow<List<TransactionGroupEntity>>

    @Query("SELECT * FROM transactions WHERE group_id = :groupId AND is_deleted = 0 ORDER BY date_time DESC")
    fun getTransactionsForGroup(groupId: Long): Flow<List<TransactionEntity>>

    /** Every grouped transaction in one query — for building all group summaries at once. */
    @Query("SELECT * FROM transactions WHERE group_id IS NOT NULL AND is_deleted = 0")
    fun getAllGroupedTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE group_id = :groupId AND is_deleted = 0")
    fun getTransactionCount(groupId: Long): Flow<Int>

    @Query("UPDATE transactions SET group_id = :groupId, updated_at = strftime('%Y-%m-%dT%H:%M:%f', 'now', 'localtime') WHERE id = :transactionId")
    suspend fun linkTransaction(transactionId: Long, groupId: Long)

    @Query("UPDATE transactions SET group_id = NULL, updated_at = strftime('%Y-%m-%dT%H:%M:%f', 'now', 'localtime') WHERE id = :transactionId")
    suspend fun unlinkTransaction(transactionId: Long)

    @Query("UPDATE transactions SET group_id = NULL, updated_at = strftime('%Y-%m-%dT%H:%M:%f', 'now', 'localtime') WHERE group_id = :groupId")
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

    /** The overlap check and the write in one transaction, so two quick saves can't both pass (#1463). */
    @Transaction
    suspend fun insertGroupChecked(group: TransactionGroupEntity): Long {
        if (group.tripStart != null) {
            findOverlappingTrip(group, getAllTrips())?.let { throw TripOverlapException(it.name) }
        }
        return insertGroup(group)
    }

    /** Checks only when the window changed, so renaming a trip that already overlaps stays possible. */
    @Transaction
    suspend fun updateGroupChecked(group: TransactionGroupEntity) {
        val stored = getGroupById(group.id)
        if (group.tripStart != null && (stored == null || stored.tripStart != group.tripStart || stored.tripEnd != group.tripEnd)) {
            findOverlappingTrip(group, getAllTrips())?.let { throw TripOverlapException(it.name) }
        }
        updateGroup(group)
    }

    @Query("SELECT * FROM transaction_groups WHERE trip_start IS NOT NULL")
    suspend fun getAllTrips(): List<TransactionGroupEntity>

    @Query("UPDATE transaction_groups SET trip_end = :end, updated_at = :end WHERE id = :groupId")
    suspend fun setTripEnd(groupId: Long, end: java.time.LocalDateTime)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTripExclusion(exclusion: TripExclusionEntity)

    @Query("DELETE FROM trip_exclusions WHERE group_id = :groupId AND transaction_hash = :hash")
    suspend fun deleteTripExclusion(groupId: Long, hash: String)

    @Query("SELECT * FROM trip_exclusions")
    suspend fun getAllTripExclusions(): List<TripExclusionEntity>

    @Query("SELECT * FROM transactions WHERE id = :transactionId")
    suspend fun getTransactionById(transactionId: Long): TransactionEntity?

    /** Takes a row out of its group; from a trip it is also remembered by hash so a rebuild can't re-add it (#1463). */
    @Transaction
    suspend fun removeFromGroup(transactionId: Long) {
        val tx = getTransactionById(transactionId)
        val group = tx?.groupId?.let { getGroupById(it) }
        tx?.let { tripExclusionFor(it, group) }?.let { insertTripExclusion(it) }
        unlinkTransaction(transactionId)
    }

    /** Putting a row back by hand clears its exclusion (#1463). */
    @Transaction
    suspend fun addToGroup(transactionId: Long, groupId: Long) {
        getTransactionById(transactionId)?.transactionHash?.let { deleteTripExclusion(groupId, it) }
        linkTransaction(transactionId, groupId)
    }

    // Used by BackupImporter.replaceAllData to clear groups before re-import.
    @Query("DELETE FROM transaction_groups")
    suspend fun deleteAllGroups()
}
