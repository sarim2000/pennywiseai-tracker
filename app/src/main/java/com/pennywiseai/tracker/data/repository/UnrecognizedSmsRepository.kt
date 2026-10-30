package com.pennywiseai.tracker.data.repository

import android.content.Context
import com.pennywiseai.tracker.data.database.dao.UnrecognizedSmsDao
import com.pennywiseai.tracker.receiver.SmsBroadcastReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing unrecognized SMS messages from financial providers.
 */
@Singleton
class UnrecognizedSmsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: UnrecognizedSmsDao
) {
    
    /**
     * Get count of unreported messages
     */
    fun getUnreportedCount(): Flow<Int> = dao.getUnreportedCount()
    
    /**
     * Get all unreported messages
     */
    fun getAllUnreported(): Flow<List<UnrecognizedSmsEntity>> = dao.getAllUnreported()
    
    /**
     * Get all visible messages (including reported, excluding deleted)
     */
    fun getAllVisible(): Flow<List<UnrecognizedSmsEntity>> = dao.getAllVisible()
    
    /**
     * Get first unreported message
     */
    suspend fun getFirstUnreported(): UnrecognizedSmsEntity? = dao.getFirstUnreported()
    
    /**
     * Insert a new unrecognized SMS
     */
    suspend fun insert(sms: UnrecognizedSmsEntity): Long = dao.insert(sms)

    suspend fun getById(id: Long): UnrecognizedSmsEntity? = dao.getById(id)

    /**
     * #994: an app notification carries no reference number, so a second identical
     * payment repeats the first one's text, which the (sender, body) index rejects.
     * Offer the earlier, already handled row again instead. Within [REDELIVERY_WINDOW]
     * it is the same alert posted twice, so -1 as for any duplicate.
     */
    suspend fun insertOrRevive(sms: UnrecognizedSmsEntity): Long {
        dao.insert(sms).takeIf { it > 0 }?.let { return it }
        val existing = dao.findBySenderAndBody(sms.sender, sms.smsBody) ?: return -1
        if (Duration.between(existing.receivedAt, sms.receivedAt).abs() < REDELIVERY_WINDOW) return -1
        // ponytail: two identical payments both still waiting show as one row; a per-event key column would split them.
        if (!existing.isDeleted) return -1
        dao.revive(existing.id, sms.receivedAt)
        return existing.id
    }

    suspend fun insertAll(smsList: List<UnrecognizedSmsEntity>) {
        dao.insertAll(smsList)
    }
    
    /**
     * Mark messages as reported
     */
    suspend fun markAsReported(ids: List<Long>) {
        if (ids.isNotEmpty()) {
            dao.markAsReported(ids)
        }
    }
    
    /**
     * Clean up old entries (older than 30 days)
     */
    suspend fun cleanupOldEntries() {
        val cutoffDate = LocalDateTime.now().minusDays(30)
        dao.deleteOldEntries(cutoffDate)
    }
    
    /**
     * Delete all entries
     */
    suspend fun deleteAll() {
        dao.deleteAll()
        // A discarded message's suggestion alert must not reopen it (#994).
        SmsBroadcastReceiver.cancelAllSuggestions(context)
    }
    
    /**
     * Soft delete a specific message by ID
     */
    suspend fun deleteMessage(id: Long) {
        dao.softDeleteById(id)
        SmsBroadcastReceiver.cancelSuggestion(context, id)
    }
    
    private companion object {
        val REDELIVERY_WINDOW: Duration = Duration.ofMinutes(2)
    }

    /**
     * Check if a message already exists (including deleted ones)
     */
    suspend fun exists(sender: String, smsBody: String): Boolean {
        return dao.findBySenderAndBody(sender, smsBody) != null
    }
}