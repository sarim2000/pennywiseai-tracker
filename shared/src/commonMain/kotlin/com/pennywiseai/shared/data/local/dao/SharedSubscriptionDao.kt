package com.pennywiseai.shared.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pennywiseai.shared.data.local.entity.SharedSubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedSubscriptionDao {
    @Query("SELECT * FROM shared_subscriptions ORDER BY updated_at_epoch_millis DESC")
    fun observeAll(): Flow<List<SharedSubscriptionEntity>>

    @Query("SELECT * FROM shared_subscriptions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SharedSubscriptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(subscription: SharedSubscriptionEntity): Long

    @Update
    suspend fun update(subscription: SharedSubscriptionEntity)

    @Query("DELETE FROM shared_subscriptions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
