package com.pennywiseai.shared.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "shared_account_balances",
    indices = [Index(value = ["bank_name", "account_last4", "timestamp_epoch_millis"])]
)
data class SharedAccountBalanceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "bank_name")
    val bankName: String,
    @ColumnInfo(name = "account_last4")
    val accountLast4: String,
    @ColumnInfo(name = "timestamp_epoch_millis")
    val timestampEpochMillis: Long,
    @ColumnInfo(name = "balance_minor")
    val balanceMinor: Long,
    @ColumnInfo(name = "transaction_id")
    val transactionId: Long? = null,
    @ColumnInfo(name = "is_credit_card")
    val isCreditCard: Boolean = false,
    @ColumnInfo(name = "account_type")
    val accountType: String? = null,
    @ColumnInfo(name = "currency")
    val currency: String = "INR",
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long
)
