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

    // Hidden categories are kept in the DB (so existing transactions keep their
    // category and still show in analytics) but are filtered out of the category
    // PICKERS. Lets users tuck away unused default categories (#736). Backup-safe:
    // a default keeps old backups restorable (#414).
    @ColumnInfo(name = "is_hidden", defaultValue = "0")
    val isHidden: Boolean = false,

    @ColumnInfo(name = "display_order")
    val displayOrder: Int = 999,
    
    @ColumnInfo(name = "created_at")
    @Contextual
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "updated_at")
    @Contextual
    val updatedAt: LocalDateTime = LocalDateTime.now()
)