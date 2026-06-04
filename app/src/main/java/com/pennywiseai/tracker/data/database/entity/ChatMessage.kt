package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID
import kotlinx.serialization.Serializable

@Entity(tableName = "chat_messages")
@Serializable
data class ChatMessage(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val message: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "false")
    val isSystemPrompt: Boolean = false // Hide from UI but include in context
)