package com.pennywiseai.tracker.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pennywiseai.tracker.data.database.entity.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface WebhookProfileDao {
    @Query("SELECT * FROM webhook_profiles ORDER BY created_at DESC") fun observeAll(): Flow<List<WebhookProfileEntity>>
    @Query("SELECT * FROM webhook_profiles WHERE enabled = 1") suspend fun enabled(): List<WebhookProfileEntity>
    @Query("SELECT * FROM webhook_profiles WHERE id = :id") suspend fun byId(id: String): WebhookProfileEntity?
    @androidx.room.Upsert suspend fun upsert(profile: WebhookProfileEntity)
    @Update suspend fun update(profile: WebhookProfileEntity)
    @Query("DELETE FROM webhook_profiles WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE webhook_profiles SET last_synced_at = :at, last_error = NULL, consecutive_failures = 0, updated_at = :at WHERE id = :id") suspend fun markSuccess(id: String, at: LocalDateTime)
    @Query("UPDATE webhook_profiles SET last_error = :error, consecutive_failures = consecutive_failures + 1, updated_at = :at WHERE id = :id") suspend fun markFailure(id: String, error: String, at: LocalDateTime)
}

@Dao
interface WebhookLogDao {
    @Insert suspend fun insert(log: WebhookLogEntity)
    @Query("SELECT * FROM webhook_logs WHERE profile_id = :profileId ORDER BY created_at DESC LIMIT 50") fun observe(profileId: String): Flow<List<WebhookLogEntity>>
    @Query("DELETE FROM webhook_logs WHERE id NOT IN (SELECT id FROM webhook_logs ORDER BY created_at DESC LIMIT 100)") suspend fun trim()
}

@Dao
interface WebhookCursorDao {
    @Query("SELECT * FROM webhook_cursors WHERE profile_id = :profileId") suspend fun forProfile(profileId: String): List<WebhookCursorEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(cursor: WebhookCursorEntity)
    @Query("DELETE FROM webhook_cursors WHERE profile_id = :profileId") suspend fun deleteForProfile(profileId: String)
}

@Dao
interface WebhookDeliveredTransactionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(receipt: WebhookDeliveredTransactionEntity)
    @Query("DELETE FROM webhook_delivered_transactions WHERE profile_id = :profileId AND transaction_id = :transactionId")
    suspend fun delete(profileId: String, transactionId: Long)
    @Query("DELETE FROM webhook_delivered_transactions WHERE profile_id = :profileId")
    suspend fun deleteForProfile(profileId: String)
}
