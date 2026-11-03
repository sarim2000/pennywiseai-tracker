package com.pennywiseai.shared.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "shared_exchange_rates",
    indices = [Index(value = ["from_currency", "to_currency"], unique = true)]
)
data class SharedExchangeRateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "from_currency")
    val fromCurrency: String,
    @ColumnInfo(name = "to_currency")
    val toCurrency: String,
    @ColumnInfo(name = "rate_micros")
    val rateMicros: Long,
    @ColumnInfo(name = "provider")
    val provider: String,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "expires_at_epoch_millis")
    val expiresAtEpochMillis: Long,
    @ColumnInfo(name = "is_custom_rate")
    val isCustomRate: Boolean = false
)
