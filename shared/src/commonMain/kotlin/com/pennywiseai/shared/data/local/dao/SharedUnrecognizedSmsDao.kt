package com.pennywiseai.shared.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pennywiseai.shared.data.local.entity.SharedUnrecognizedSmsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedUnrecognizedSmsDao {
    @Query("SELECT * FROM shared_unrecognized_sms WHERE is_deleted = 0 ORDER BY received_at_epoch_millis DESC")
    fun observeVisible(): Flow<List<SharedUnrecognizedSmsEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: SharedUnrecognizedSmsEntity): Long

    @Query("UPDATE shared_unrecognized_sms SET reported = 1 WHERE id = :id")
    suspend fun markReported(id: Long)

    @Query("UPDATE shared_unrecognized_sms SET is_deleted = 1 WHERE id = :id")
    suspend fun softDelete(id: Long)
}
