package com.pennywiseai.tracker.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/**
 * DAO for managing unrecognized SMS messages from potential financial providers.
 */
@Dao
interface UnrecognizedSmsDao {
    
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insert(sms: UnrecognizedSmsEntity): Long
    
    @Query("SELECT * FROM unrecognized_sms WHERE reported = 0 AND is_deleted = 0 ORDER BY received_at DESC")
    fun getAllUnreported(): Flow<List<UnrecognizedSmsEntity>>
    
    @Query("SELECT * FROM unrecognized_sms WHERE is_deleted = 0 ORDER BY received_at DESC")
    fun getAllVisible(): Flow<List<UnrecognizedSmsEntity>>

    @Query("SELECT * FROM unrecognized_sms WHERE is_deleted = 0 ORDER BY received_at DESC")
    suspend fun getAllVisibleSnapshot(): List<UnrecognizedSmsEntity>
    
    @Query("SELECT * FROM unrecognized_sms ORDER BY received_at DESC")
    fun getAllUnrecognizedSms(): Flow<List<UnrecognizedSmsEntity>>
    
    @Query("SELECT * FROM unrecognized_sms WHERE reported = 0 AND is_deleted = 0 ORDER BY received_at DESC LIMIT 1")
    suspend fun getFirstUnreported(): UnrecognizedSmsEntity?
    
    @Query("SELECT COUNT(*) FROM unrecognized_sms WHERE reported = 0 AND is_deleted = 0")
    fun getUnreportedCount(): Flow<Int>
    
    @Query("UPDATE unrecognized_sms SET reported = 1 WHERE id IN (:ids)")
    suspend fun markAsReported(ids: List<Long>)
    
    @Query("UPDATE unrecognized_sms SET is_deleted = 1 WHERE received_at < :cutoffDate")
    suspend fun deleteOldEntries(cutoffDate: LocalDateTime)
    
    @Query("UPDATE unrecognized_sms SET is_deleted = 1")
    suspend fun deleteAll()
    
    @Query("UPDATE unrecognized_sms SET is_deleted = 1 WHERE id = :id")
    suspend fun softDeleteById(id: Long)
    
    @Query("SELECT * FROM unrecognized_sms WHERE sender = :sender AND sms_body = :smsBody LIMIT 1")
    suspend fun findBySenderAndBody(sender: String, smsBody: String): UnrecognizedSmsEntity?
}