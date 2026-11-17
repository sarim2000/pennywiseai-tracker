package com.pennywiseai.tracker.data.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

@Entity(tableName = "chat_messages")
@Serializable
data class ChatMessage(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val message: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    // Hide from UI but include in context
    @ColumnInfo(defaultValue = "false")
    val isSystemPrompt: Boolean = false
)
