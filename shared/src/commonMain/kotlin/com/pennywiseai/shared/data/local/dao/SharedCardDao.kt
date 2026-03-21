package com.pennywiseai.shared.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pennywiseai.shared.data.local.entity.SharedCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedCardDao {
    @Query("SELECT * FROM shared_cards ORDER BY updated_at_epoch_millis DESC")
    fun observeAll(): Flow<List<SharedCardEntity>>

    @Query("SELECT * FROM shared_cards WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SharedCardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: SharedCardEntity): Long

    @Update
    suspend fun update(card: SharedCardEntity)

    @Query("DELETE FROM shared_cards WHERE id = :id")
    suspend fun deleteById(id: Long)
}
