package com.pennywiseai.tracker.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.pennywiseai.tracker.data.database.entity.BankNotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BankNotificationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(notification: BankNotificationEntity): Long

    @Query(
        """
        UPDATE bank_notifications
        SET processed = :processed, transaction_id = :transactionId
        WHERE id = :id
        """
    )
    suspend fun updateStatus(id: Long, processed: Boolean, transactionId: Long?)

    @Query("SELECT * FROM bank_notifications WHERE processed = 0 ORDER BY posted_at ASC")
    suspend fun getUnprocessed(): List<BankNotificationEntity>

    @Query("SELECT * FROM bank_notifications ORDER BY posted_at ASC")
    fun getAllNotifications(): Flow<List<BankNotificationEntity>>

    @Query("DELETE FROM bank_notifications")
    suspend fun deleteAllNotifications()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(notification: BankNotificationEntity): Long
}
