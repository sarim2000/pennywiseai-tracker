package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
@Serializable
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "color")
    val color: String,
    
    @ColumnInfo(name = "is_system")
    val isSystem: Boolean = false,
    
    @ColumnInfo(name = "is_income")
    val isIncome: Boolean = false,
    
    @ColumnInfo(name = "display_order")
    val displayOrder: Int = 999,
    
    @ColumnInfo(name = "created_at")
    @Contextual
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "updated_at")
    @Contextual
    val updatedAt: LocalDateTime = LocalDateTime.now()
)