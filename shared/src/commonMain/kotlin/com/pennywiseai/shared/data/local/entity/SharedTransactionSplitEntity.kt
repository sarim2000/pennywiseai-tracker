package com.pennywiseai.shared.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "shared_transaction_splits",
    foreignKeys = [
        ForeignKey(
            entity = SharedTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["transaction_id"])]
)
data class SharedTransactionSplitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "transaction_id")
    val transactionId: Long,
    @ColumnInfo(name = "category")
    val category: String,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long
)
