package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Entity(
    tableName = "loans",
    indices = [
        Index(value = ["status"]),
        Index(value = ["person_name"]),
        Index(value = ["created_at"])
    ]
)
@Serializable
data class LoanEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "person_name")
    val personName: String,

    @ColumnInfo(name = "direction")
    val direction: LoanDirection,

    @ColumnInfo(name = "original_amount")
    @Contextual
    val originalAmount: BigDecimal,

    @ColumnInfo(name = "remaining_amount")
    @Contextual
    val remainingAmount: BigDecimal,

    @ColumnInfo(name = "currency", defaultValue = "INR")
    val currency: String = "INR",

    @ColumnInfo(name = "status", defaultValue = "ACTIVE")
    val status: LoanStatus = LoanStatus.ACTIVE,

    @ColumnInfo(name = "note")
    val note: String? = null,

    @ColumnInfo(name = "created_at")
    @Contextual
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "updated_at")
    @Contextual
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "settled_at")
    @Contextual
    val settledAt: LocalDateTime? = null
)

/**
 * One person's share of a split bill (#346): [amount] of transaction
 * [transactionId] is owed to you under loan [loanId], in the transaction's
 * currency. It counts toward the loan's principal like a linked transaction.
 * The transaction's `lent_shares_total` is the sum of its rows (LoanRepository
 * keeps it in step), so totals count only your part.
 */
@Entity(
    tableName = "loan_shares",
    foreignKeys = [
        ForeignKey(entity = LoanEntity::class, parentColumns = ["id"], childColumns = ["loan_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transaction_id"], onDelete = ForeignKey.CASCADE)
    ],
    // One share per person (loan) per transaction; also makes a repeat backup merge idempotent.
    indices = [Index(value = ["transaction_id", "loan_id"], unique = true), Index("loan_id")]
)
@Serializable
data class LoanShareEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "transaction_id")
    val transactionId: Long = 0,

    @ColumnInfo(name = "loan_id")
    val loanId: Long = 0,

    @ColumnInfo(name = "amount")
    @Contextual
    val amount: BigDecimal = BigDecimal.ZERO,

    @ColumnInfo(name = "created_at")
    @Contextual
    val createdAt: LocalDateTime = LocalDateTime.now()
)

@Serializable
enum class LoanDirection {
    LENT,
    BORROWED
}

@Serializable
enum class LoanStatus {
    ACTIVE,
    SETTLED
}
