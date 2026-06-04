package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Entity for storing unrecognized SMS messages from potential financial providers.
 * Only stores messages from senders ending with -T (transaction) or -S (service) suffixes,
 * which indicate DLT-registered financial service providers.
 */
@Entity(
    tableName = "unrecognized_sms",
    indices = [
        androidx.room.Index(
            value = ["sender", "sms_body"],
            unique = true
        )
    ]
)
@Serializable
data class UnrecognizedSmsEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    @ColumnInfo(name = "sender")
    val sender: String,
    
    @ColumnInfo(name = "sms_body")
    val smsBody: String,
    
    @ColumnInfo(name = "received_at")
    @Contextual
    val receivedAt: LocalDateTime,
    
    @ColumnInfo(name = "reported")
    val reported: Boolean = false,
    
    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Boolean = false,
    
    @ColumnInfo(name = "created_at")
    @Contextual
    val createdAt: LocalDateTime = LocalDateTime.now()
)