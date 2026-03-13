package com.pennywiseai.shared.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shared_budget_categories",
    foreignKeys = [
        ForeignKey(
            entity = SharedBudgetEntity::class,
            parentColumns = ["id"],
            childColumns = ["budget_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["budget_id", "category_name"], unique = true)]
)
data class SharedBudgetCategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "budget_id")
    val budgetId: Long,
    @ColumnInfo(name = "category_name")
    val categoryName: String,
    @ColumnInfo(name = "budget_amount_minor")
    val budgetAmountMinor: Long
)
