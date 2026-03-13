package com.pennywiseai.shared.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shared_budgets")
data class SharedBudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "limit_minor")
    val limitMinor: Long,
    @ColumnInfo(name = "period_type")
    val periodType: String,
    @ColumnInfo(name = "start_epoch_millis")
    val startEpochMillis: Long,
    @ColumnInfo(name = "end_epoch_millis")
    val endEpochMillis: Long,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "group_type")
    val groupType: String,
    @ColumnInfo(name = "currency")
    val currency: String = "INR",
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long
)
