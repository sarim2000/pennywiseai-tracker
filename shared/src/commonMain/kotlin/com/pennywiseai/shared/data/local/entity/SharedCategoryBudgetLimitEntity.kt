package com.pennywiseai.shared.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "shared_category_budget_limits",
    indices = [Index(value = ["category_name"], unique = true)]
)
data class SharedCategoryBudgetLimitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "category_name")
    val categoryName: String,
    @ColumnInfo(name = "limit_amount_minor")
    val limitAmountMinor: Long
)
