package com.pennywiseai.shared.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shared_unrecognized_sms",
    indices = [Index(value = ["sender", "sms_body"], unique = true)]
)
data class SharedUnrecognizedSmsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "sender")
    val sender: String,
    @ColumnInfo(name = "sms_body")
    val smsBody: String,
    @ColumnInfo(name = "received_at_epoch_millis")
    val receivedAtEpochMillis: Long,
    @ColumnInfo(name = "reported")
    val reported: Boolean = false,
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long
)
