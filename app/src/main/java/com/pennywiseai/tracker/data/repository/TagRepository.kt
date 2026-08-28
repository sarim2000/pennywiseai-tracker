package com.pennywiseai.tracker.data.repository

import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.TagDao
import com.pennywiseai.tracker.data.database.entity.TagEntity
import com.pennywiseai.tracker.data.database.entity.TransactionTagCrossRef
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the many-to-many relationship between transactions and free-text
 * [TagEntity]s. Tags are created on demand: setting a tag name that doesn't
 * exist yet inserts a new row, otherwise the existing tag is reused.
 */
@Singleton
class TagRepository @Inject constructor(
    private val database: PennyWiseDatabase,
    private val tagDao: TagDao
) {
    private companion object {
        const val TAG = "TagRepository"
    }

    // Ordered, single-consumer queue for tag writes (#710). setTagsForTransaction
    // is a replace-all, so concurrent callers (e.g. rapid edits in the quick
    // picker) could otherwise land out of order and persist a stale set, or lose
    // one transaction's edits when the caller retargets to another. Enqueuing
    // preserves send order and a single consumer applies them FIFO.
    private val tagWriteScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tagWriteQueue = Channel<Pair<Long, List<String>>>(Channel.UNLIMITED)

    init {
        tagWriteScope.launch {
            for ((transactionId, tagNames) in tagWriteQueue) {
                runCatching { setTagsForTransaction(transactionId, tagNames) }
                    .onFailure { e -> Log.e(TAG, "Queued tag write failed: txId=$transactionId", e) }
            }
        }
    }

    /**
     * Enqueue a replace-all tag write applied in FIFO order on a single
     * consumer, so rapid or interleaved calls can't reorder or lose data.
     * Prefer this over calling [setTagsForTransaction] directly from UI that
     * fires many quick edits.
     */
    fun enqueueSetTags(transactionId: Long, tagNames: List<String>) {
        tagWriteQueue.trySend(transactionId to tagNames)
    }

    fun observeAllTags(): Flow<List<TagEntity>> = tagDao.getAllTags()

    /** All tag names currently in use, for autocomplete suggestions. */
    fun observeAllTagNames(): Flow<List<String>> =
        tagDao.getAllTags().map { tags -> tags.map { it.name } }

    fun observeTagsForTransaction(transactionId: Long): Flow<List<TagEntity>> =
        tagDao.getTagsForTransaction(transactionId)

    fun observeTagNamesForTransaction(transactionId: Long): Flow<List<String>> =
        tagDao.getTagsForTransaction(transactionId).map { tags -> tags.map { it.name } }

    /**
     * Emits a map of transactionId -> list of tag names, used by search and
     * analytics which need every transaction's tags in one pass.
     */
    fun observeTransactionTagNames(): Flow<Map<Long, List<String>>> =
        tagDao.getAllTransactionTagPairs().map { pairs ->
            pairs.groupBy({ it.transactionId }, { it.name })
        }

    suspend fun getTagNamesForTransaction(transactionId: Long): List<String> =
        tagDao.getTagsForTransactionSync(transactionId).map { it.name }

    /**
     * Returns the id of the tag with [name], creating it if necessary. Returns
     * -1 for a blank name (which is never persisted).
     */
    suspend fun getOrCreateTag(name: String): Long {
        val normalized = name.trim()
        if (normalized.isEmpty()) return -1L
        tagDao.getTagByName(normalized)?.let { return it.id }
        val inserted = tagDao.insertTag(TagEntity(name = normalized))
        // IGNORE conflict returns -1 on a race; re-read the existing row.
        return if (inserted != -1L) inserted else tagDao.getTagByName(normalized)?.id ?: -1L
    }

    /**
     * Replaces the full set of tags on a transaction with [tagNames]
     * (deduplicated, case-insensitively). Creates any missing tags and prunes
     * tags that no longer reference any transaction.
     */
    suspend fun setTagsForTransaction(transactionId: Long, tagNames: List<String>) {
        // Atomic: a crash/cancellation between the delete and the re-insert would
        // otherwise silently strip a transaction's tags. Room's withTransaction is
        // reentrant, so getOrCreateTag's inner DAO calls join this transaction.
        database.withTransaction {
            tagDao.deleteCrossRefsForTransaction(transactionId)

            val tagIds = tagNames
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .map { getOrCreateTag(it) }
                .filter { it > 0L }
                .distinct()

            if (tagIds.isNotEmpty()) {
                tagDao.insertCrossRefs(tagIds.map { TransactionTagCrossRef(transactionId, it) })
            }
            tagDao.deleteOrphanTags()
        }
    }
}
