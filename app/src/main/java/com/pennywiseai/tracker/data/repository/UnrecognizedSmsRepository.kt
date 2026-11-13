package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.UnrecognizedSmsDao
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
    private val dao: UnrecognizedSmsDao,
    private val alerts: UnrecognizedSmsAlerts
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

    suspend fun find(sender: String, body: String): UnrecognizedSmsEntity? = dao.findBySenderAndBody(sender, body)

    /**
     * This message is dealt with: hide it, and leave a hidden row (even if it never had one,
     * as when picked from the inbox) so the next scan doesn't offer it again.
     */
    suspend fun settle(sender: String, body: String, receivedAt: LocalDateTime) {
        val id = dao.findBySenderAndBody(sender, body)?.id
            ?: dao.insert(UnrecognizedSmsEntity(sender = sender, smsBody = body, receivedAt = receivedAt)).takeIf { it > 0 }
            ?: return
        dao.softDeleteById(id)
        alerts.cancel(id)
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
        alerts.cancelAll()
    }

    /**
     * Soft delete a specific message by ID
     */
    suspend fun deleteMessage(id: Long) {
        dao.softDeleteById(id)
        alerts.cancel(id)
    }

    companion object {
        /** Identical text this close together is one SMS delivered or stamped twice, not a second payment. */
        val REDELIVERY_WINDOW: Duration = Duration.ofMinutes(2)
    }

    /**
     * Check if a message already exists (including deleted ones)
     */
    suspend fun exists(sender: String, smsBody: String): Boolean {
        return dao.findBySenderAndBody(sender, smsBody) != null
    }
}
