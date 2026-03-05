package com.pennywiseai.shared.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shared_categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class SharedCategoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "color_hex")
    val colorHex: String,
    @ColumnInfo(name = "is_system", defaultValue = "0")
    val isSystem: Boolean = false,
    @ColumnInfo(name = "is_income", defaultValue = "0")
    val isIncome: Boolean = false,
    @ColumnInfo(name = "display_order", defaultValue = "0")
    val displayOrder: Int = 0,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long
)
