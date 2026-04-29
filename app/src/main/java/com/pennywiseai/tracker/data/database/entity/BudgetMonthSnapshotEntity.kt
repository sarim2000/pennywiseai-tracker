package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "budget_month_snapshots")
data class BudgetMonthSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "budget_id")
    val budgetId: Long,

    @ColumnInfo(name = "year")
    val year: Int,

    @ColumnInfo(name = "month")
    val month: Int,

    @ColumnInfo(name = "budget_name")
    val budgetName: String,

    @ColumnInfo(name = "limit_amount")
    val limitAmount: BigDecimal,

    @ColumnInfo(name = "include_all_categories", defaultValue = "0")
    val includeAllCategories: Boolean = false,

    @ColumnInfo(name = "color", defaultValue = "#1565C0")
    val color: String = "#1565C0",

    @ColumnInfo(name = "group_type", defaultValue = "LIMIT")
    val groupType: BudgetGroupType = BudgetGroupType.LIMIT,

    @ColumnInfo(name = "display_order", defaultValue = "0")
    val displayOrder: Int = 0
)
