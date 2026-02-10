package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(
    tableName = "budgets",
    indices = [
        Index(value = ["name"]),
        Index(value = ["is_active"])
    ]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "limit_amount")
    val limitAmount: BigDecimal,

    @ColumnInfo(name = "period_type")
    val periodType: BudgetPeriodType,

    @ColumnInfo(name = "start_date")
    val startDate: LocalDate,

    @ColumnInfo(name = "end_date")
    val endDate: LocalDate,

    @ColumnInfo(name = "currency", defaultValue = "INR")
    val currency: String = "INR",

    @ColumnInfo(name = "is_active", defaultValue = "1")
    val isActive: Boolean = true,

    @ColumnInfo(name = "include_all_categories", defaultValue = "0")
    val includeAllCategories: Boolean = false,

    @ColumnInfo(name = "color", defaultValue = "#1565C0")
    val color: String = "#1565C0",

    @ColumnInfo(name = "group_type", defaultValue = "LIMIT")
    val groupType: BudgetGroupType = BudgetGroupType.LIMIT,

    @ColumnInfo(name = "display_order", defaultValue = "0")
    val displayOrder: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class BudgetPeriodType {
    WEEKLY,
    MONTHLY,
    CUSTOM
}

enum class BudgetGroupType {
    LIMIT,
    TARGET,
    EXPECTED
}
