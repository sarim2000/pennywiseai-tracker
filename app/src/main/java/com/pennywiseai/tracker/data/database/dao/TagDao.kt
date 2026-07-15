package com.pennywiseai.tracker.data.database.dao

import androidx.room.*
import com.pennywiseai.tracker.data.database.entity.TagEntity
import com.pennywiseai.tracker.data.database.entity.TransactionTagCrossRef
import com.pennywiseai.tracker.data.database.entity.TransactionTagName
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    // ── Tags ──────────────────────────────────────────────────────────────

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    fun getAllTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllTagsSync(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getTagByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<TagEntity>)

    @Query("DELETE FROM tags")
    suspend fun deleteAllTags()

    /** Removes tags that are no longer referenced by any transaction. */
    @Query("DELETE FROM tags WHERE id NOT IN (SELECT DISTINCT tag_id FROM transaction_tag_cross_ref)")
    suspend fun deleteOrphanTags()

    // ── Cross references ─────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(ref: TransactionTagCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(refs: List<TransactionTagCrossRef>)

    @Query("DELETE FROM transaction_tag_cross_ref WHERE transaction_id = :transactionId")
    suspend fun deleteCrossRefsForTransaction(transactionId: Long)

    @Query("DELETE FROM transaction_tag_cross_ref")
    suspend fun deleteAllCrossRefs()

    @Query("SELECT * FROM transaction_tag_cross_ref")
    suspend fun getAllCrossRefs(): List<TransactionTagCrossRef>

    // ── Joins ─────────────────────────────────────────────────────────────

    @Query(
        """
        SELECT t.* FROM tags t
        INNER JOIN transaction_tag_cross_ref cr ON t.id = cr.tag_id
        WHERE cr.transaction_id = :transactionId
        ORDER BY t.name COLLATE NOCASE ASC
        """
    )
    fun getTagsForTransaction(transactionId: Long): Flow<List<TagEntity>>

    @Query(
        """
        SELECT t.* FROM tags t
        INNER JOIN transaction_tag_cross_ref cr ON t.id = cr.tag_id
        WHERE cr.transaction_id = :transactionId
        ORDER BY t.name COLLATE NOCASE ASC
        """
    )
    suspend fun getTagsForTransactionSync(transactionId: Long): List<TagEntity>

    @Query(
        """
        SELECT cr.transaction_id AS transactionId, t.name AS name
        FROM transaction_tag_cross_ref cr
        INNER JOIN tags t ON cr.tag_id = t.id
        """
    )
    fun getAllTransactionTagPairs(): Flow<List<TransactionTagName>>
}
