package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity(
    tableName = "loans",
    indices = [
        Index(value = ["status"]),
        Index(value = ["person_name"]),
        Index(value = ["created_at"])
    ]
)
data class LoanEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "person_name")
    val personName: String,

    @ColumnInfo(name = "direction")
    val direction: LoanDirection,

    @ColumnInfo(name = "original_amount")
    val originalAmount: BigDecimal,

    @ColumnInfo(name = "remaining_amount")
    val remainingAmount: BigDecimal,

    @ColumnInfo(name = "currency", defaultValue = "INR")
    val currency: String = "INR",

    @ColumnInfo(name = "status", defaultValue = "ACTIVE")
    val status: LoanStatus = LoanStatus.ACTIVE,

    @ColumnInfo(name = "note")
    val note: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "settled_at")
    val settledAt: LocalDateTime? = null
)

enum class LoanDirection {
    LENT,
    BORROWED
}

enum class LoanStatus {
    ACTIVE,
    SETTLED
}
