package com.pennywiseai.shared.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shared_cards",
    indices = [Index(value = ["bank_name", "card_last4"], unique = true)]
)
data class SharedCardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "card_last4")
    val cardLast4: String,
    @ColumnInfo(name = "card_type")
    val cardType: String = "CREDIT",
    @ColumnInfo(name = "bank_name")
    val bankName: String,
    @ColumnInfo(name = "account_last4")
    val accountLast4: String? = null,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "last_balance_minor")
    val lastBalanceMinor: Long? = null,
    @ColumnInfo(name = "currency")
    val currency: String = "INR",
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long
)
