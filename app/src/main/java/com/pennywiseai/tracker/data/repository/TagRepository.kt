package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.TagDao
import com.pennywiseai.tracker.data.database.entity.TagEntity
import com.pennywiseai.tracker.data.database.entity.TransactionTagCrossRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the many-to-many relationship between transactions and free-text
 * [TagEntity]s. Tags are created on demand: setting a tag name that doesn't
 * exist yet inserts a new row, otherwise the existing tag is reused.
 */
@Singleton
class TagRepository @Inject constructor(
    private val tagDao: TagDao
) {

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
