package com.pennywiseai.shared.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pennywiseai.shared.data.local.entity.SharedExchangeRateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedExchangeRateDao {
    @Query("SELECT * FROM shared_exchange_rates ORDER BY updated_at_epoch_millis DESC")
    fun observeAll(): Flow<List<SharedExchangeRateEntity>>

    @Query("SELECT * FROM shared_exchange_rates WHERE from_currency = :fromCurrency AND to_currency = :toCurrency LIMIT 1")
    suspend fun getRate(fromCurrency: String, toCurrency: String): SharedExchangeRateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rate: SharedExchangeRateEntity): Long

    @Query("DELETE FROM shared_exchange_rates WHERE expires_at_epoch_millis < :epochMillis")
    suspend fun deleteExpired(epochMillis: Long)
}
