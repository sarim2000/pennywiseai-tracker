package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.UnrecognizedSmsDao
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing unrecognized SMS messages from financial providers.
 */
@Singleton
class UnrecognizedSmsRepository @Inject constructor(
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
    }
    
    /**
     * Soft delete a specific message by ID
     */
    suspend fun deleteMessage(id: Long) {
        dao.softDeleteById(id)
    }
    
    /**
     * Check if a message already exists (including deleted ones)
     */
    suspend fun exists(sender: String, smsBody: String): Boolean {
        return dao.findBySenderAndBody(sender, smsBody) != null
    }
}