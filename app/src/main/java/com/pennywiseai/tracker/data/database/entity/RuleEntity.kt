package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Entity(
    tableName = "transaction_rules",
    indices = [
        Index(value = ["priority", "is_active"]),
        Index(value = ["name"])
    ]
)
@Serializable
data class RuleEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String?,

    @ColumnInfo(name = "priority")
    val priority: Int,

    @ColumnInfo(name = "conditions")
    val conditions: String, // JSON string

    @ColumnInfo(name = "actions")
    val actions: String, // JSON string

    @ColumnInfo(name = "is_active")
    val isActive: Boolean,

    @ColumnInfo(name = "is_system_template")
    val isSystemTemplate: Boolean = false,

    @ColumnInfo(name = "created_at")
    @Contextual
    val createdAt: LocalDateTime,

    @ColumnInfo(name = "updated_at")
    @Contextual
    val updatedAt: LocalDateTime
)