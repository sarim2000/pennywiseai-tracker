package com.pennywiseai.shared.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shared_transactions",
    indices = [
        Index(value = ["occurred_at_epoch_millis"]),
        Index(value = ["transaction_hash"], unique = true),
        Index(value = ["reference"])
    ]
)
data class SharedTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "merchant_name")
    val merchantName: String,
    @ColumnInfo(name = "category")
    val category: String,
    @ColumnInfo(name = "transaction_type")
    val transactionType: String,
    @ColumnInfo(name = "occurred_at_epoch_millis")
    val occurredAtEpochMillis: Long,
    @ColumnInfo(name = "note")
    val note: String? = null,
    @ColumnInfo(name = "currency", defaultValue = "INR")
    val currency: String = "INR",
    @ColumnInfo(name = "transaction_hash")
    val transactionHash: String? = null,
    @ColumnInfo(name = "reference")
    val reference: String? = null,
    @ColumnInfo(name = "bank_name")
    val bankName: String? = null,
    @ColumnInfo(name = "account_last4")
    val accountLast4: String? = null,
    @ColumnInfo(name = "balance_after_minor")
    val balanceAfterMinor: Long? = null,
    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Boolean = false,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long = occurredAtEpochMillis,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = occurredAtEpochMillis
)
