package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Entity(tableName = "budget_category_month_snapshots")
@Serializable
data class BudgetCategoryMonthSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "budget_id")
    val budgetId: Long,

    @ColumnInfo(name = "year")
    val year: Int,

    @ColumnInfo(name = "month")
    val month: Int,

    @ColumnInfo(name = "category_name")
    val categoryName: String,

    @ColumnInfo(name = "budget_amount")
    @Contextual
    val budgetAmount: BigDecimal
)
