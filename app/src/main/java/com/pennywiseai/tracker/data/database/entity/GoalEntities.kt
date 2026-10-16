package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * A Goal: an amount to set aside by a Due date, in one currency (#914, see
 * GLOSSARY.md and docs/adr/0001). Separate from Budgets on purpose.
 *
 * ## Backup contract (#414)
 * Every field has a Kotlin default so an older backup that omits any key still
 * restores (docs/backup-format.md, enforced by `BackupSchemaGuardTest`).
 */
@Entity(tableName = "goals", indices = [Index(value = ["uid"], unique = true)])
@Serializable
data class GoalEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** Stable identity carried through backups; merge-restore dedups on it. */
    @ColumnInfo(name = "uid")
    val uid: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "name")
    val name: String = "",

    @ColumnInfo(name = "target_amount")
    @Contextual
    val targetAmount: BigDecimal = BigDecimal.ZERO,

    /** The goal's one currency. Immutable once the goal has a Contribution. */
    @ColumnInfo(name = "currency")
    val currency: String = "INR",

    @ColumnInfo(name = "due_date")
    @Contextual
    val dueDate: LocalDate = LocalDate.now(),

    @ColumnInfo(name = "color")
    val color: String = "#1565C0",

    /** "Already saved" before the goal was created, counted from day one. */
    @ColumnInfo(name = "already_saved")
    @Contextual
    val alreadySaved: BigDecimal = BigDecimal.ZERO,

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,

    /** Start of the even pace that "Behind" is measured against. */
    @ColumnInfo(name = "created_at")
    @Contextual
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "updated_at")
    @Contextual
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)

/**
 * A Contribution (positive [amount]) or Withdrawal (negative [amount]) to a
 * Goal, always in the goal's currency. Only ever created by the user (ADR 0002).
 *
 * When it came from another currency or from a transaction, [originalAmount] /
 * [originalCurrency] keep the pre-conversion amount (same sign as [amount]).
 * For a transaction-linked row that is the share of the transaction, in the
 * transaction's currency; a transaction's linked shares never exceed its amount.
 *
 * A row linked to a soft-deleted transaction doesn't count toward Saved (undo
 * brings it back); a hard delete of the transaction cascades.
 */
@Entity(
    tableName = "goal_contributions",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goal_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("goal_id"), Index("transaction_id"), Index(value = ["uid"], unique = true)]
)
@Serializable
data class GoalContributionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "uid")
    val uid: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "goal_id")
    val goalId: Long = 0,

    /** Signed, in the goal's currency: positive = Contribution, negative = Withdrawal. */
    @ColumnInfo(name = "amount")
    @Contextual
    val amount: BigDecimal = BigDecimal.ZERO,

    @ColumnInfo(name = "date")
    @Contextual
    val date: LocalDate = LocalDate.now(),

    @ColumnInfo(name = "note")
    val note: String? = null,

    @ColumnInfo(name = "original_amount")
    @Contextual
    val originalAmount: BigDecimal? = null,

    @ColumnInfo(name = "original_currency")
    val originalCurrency: String? = null,

    @ColumnInfo(name = "transaction_id")
    val transactionId: Long? = null,

    @ColumnInfo(name = "created_at")
    @Contextual
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "updated_at")
    @Contextual
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
