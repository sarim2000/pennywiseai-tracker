package com.pennywiseai.tracker.data.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import java.time.LocalDateTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Entity(
    tableName = "bank_notifications",
    indices = [
        Index(value = ["package_name", "message_hash"], unique = true)
    ]
)
@Serializable
data class BankNotificationEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "sender_alias")
    val senderAlias: String,

    @ColumnInfo(name = "message_body")
    val messageBody: String,

    @ColumnInfo(name = "message_hash")
    val messageHash: String,

    @ColumnInfo(name = "posted_at")
    @Contextual
    val postedAt: LocalDateTime,

    @ColumnInfo(name = "processed")
    val processed: Boolean = false,

    @ColumnInfo(name = "transaction_id")
    val transactionId: Long? = null,

    @ColumnInfo(name = "created_at")
    @Contextual
    val createdAt: LocalDateTime = LocalDateTime.now()
)
