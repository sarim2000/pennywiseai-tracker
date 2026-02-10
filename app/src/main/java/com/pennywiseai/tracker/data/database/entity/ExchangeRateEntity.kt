package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity(
    tableName = "exchange_rates",
    indices = [
        Index(value = ["from_currency", "to_currency"], unique = true),
        Index(value = ["from_currency"]),
        Index(value = ["to_currency"]),
        Index(value = ["updated_at"]),
        Index(value = ["expires_at_unix"])
    ]
)
data class ExchangeRateEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "from_currency")
    val fromCurrency: String,

    @ColumnInfo(name = "to_currency")
    val toCurrency: String,

    @ColumnInfo(name = "rate")
    val rate: BigDecimal,

    @ColumnInfo(name = "provider")
    val provider: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime,

    @ColumnInfo(name = "updated_at_unix", defaultValue = "0")
    val updatedAtUnix: Long = 0,

    @ColumnInfo(name = "expires_at")
    val expiresAt: LocalDateTime,

    @ColumnInfo(name = "expires_at_unix", defaultValue = "0")
    val expiresAtUnix: Long = 0,

    @ColumnInfo(name = "is_custom_rate", defaultValue = "0")
    val isCustomRate: Boolean = false
)