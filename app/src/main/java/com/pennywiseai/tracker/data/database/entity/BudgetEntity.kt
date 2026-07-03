package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Entity(
    tableName = "budgets",
    indices = [
        Index(value = ["name"]),
        Index(value = ["is_active"])
    ]
)
@Serializable
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "limit_amount")
    @Contextual
    val limitAmount: BigDecimal,

    @ColumnInfo(name = "period_type")
    val periodType: BudgetPeriodType,

    @ColumnInfo(name = "start_date")
    @Contextual
    val startDate: LocalDate,

    @ColumnInfo(name = "end_date")
    @Contextual
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
    @Contextual
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "updated_at")
    @Contextual
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

@Serializable
enum class BudgetPeriodType {
    WEEKLY,
    MONTHLY,
    CUSTOM
}

@Serializable
enum class BudgetGroupType {
    LIMIT,
    TARGET,
    EXPECTED
}
