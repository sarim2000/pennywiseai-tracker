package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * A user-defined recurring / scheduled MANUAL transaction template (#706).
 *
 * Unlike [SubscriptionEntity] — which is derived from bank SMS mandates and is
 * matched against incoming debit SMS — a recurring transaction template has no
 * SMS behind it. It exists for cash / manual spend (rent paid in cash, a weekly
 * allowance, a monthly cleaner) that the user wants materialised automatically
 * on a schedule. A daily worker ([com.pennywiseai.tracker.worker.RecurringTransactionWorker])
 * calls the repository's `materializeDue`, which inserts a real
 * [TransactionEntity] for every template whose [nextDueDate] has arrived and
 * advances [nextDueDate] by the template's [frequency].
 *
 * ## Backup contract (#414)
 * Every field has a Kotlin default so an older backup that omits any key still
 * restores (see docs/backup-format.md, enforced by `BackupSchemaGuardTest`).
 */
@Entity(tableName = "recurring_transactions")
@Serializable
data class RecurringTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /**
     * Stable synthetic identity assigned at creation and carried through
     * backups. Backup restore dedups on this (not on mutable content), so a
     * repeated restore of the same backup never duplicates a template, while
     * genuinely distinct templates always have distinct uids. (#706)
     */
    @ColumnInfo(name = "uid", defaultValue = "")
    val uid: String = java.util.UUID.randomUUID().toString(),

    @ColumnInfo(name = "merchant_name")
    val merchantName: String = "",

    @ColumnInfo(name = "amount")
    @Contextual
    val amount: BigDecimal = BigDecimal.ZERO,

    @ColumnInfo(name = "currency", defaultValue = "INR")
    val currency: String = "INR",

    @ColumnInfo(name = "category", defaultValue = "Others")
    val category: String = "Others",

    @ColumnInfo(name = "transaction_type", defaultValue = "EXPENSE")
    val transactionType: TransactionType = TransactionType.EXPENSE,

    @ColumnInfo(name = "frequency", defaultValue = "MONTHLY")
    val frequency: RecurringFrequency = RecurringFrequency.MONTHLY,

    /** Day-of-month (1..31) the charge lands on, for MONTHLY. */
    @ColumnInfo(name = "day_of_month")
    val dayOfMonth: Int? = null,

    /** ISO day-of-week (1=Mon .. 7=Sun) the charge lands on, for WEEKLY. */
    @ColumnInfo(name = "day_of_week")
    val dayOfWeek: Int? = null,

    @ColumnInfo(name = "next_due_date")
    @Contextual
    val nextDueDate: LocalDate = LocalDate.now(),

    @ColumnInfo(name = "is_active", defaultValue = "1")
    val isActive: Boolean = true,

    /**
     * Optional funding account. When both are null the template is treated as
     * cash (no account balance is moved when it materialises). When set they
     * pair to identify the exact account, matching how the rest of the app
     * keys accounts by (bank_name + account_last4).
     */
    @ColumnInfo(name = "account_last4")
    val accountLast4: String? = null,

    @ColumnInfo(name = "bank_name")
    val bankName: String? = null,

    @ColumnInfo(name = "note")
    val note: String? = null,

    @ColumnInfo(name = "profile_id", defaultValue = "1")
    val profileId: Long = 1,

    @ColumnInfo(name = "created_at")
    @Contextual
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "updated_at")
    @Contextual
    val updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    /**
     * The next due date strictly after [from], **re-anchored** to this
     * template's [dayOfMonth] / [dayOfWeek]. Stepping off the previous
     * (possibly day-clamped) date would let a day-31 monthly template drift to
     * the 28th permanently after February; anchoring to [dayOfMonth] each step
     * keeps it on the 31st (clamped only for that short month). #706
     */
    fun nextDueAfter(from: LocalDate): LocalDate = when (frequency) {
        RecurringFrequency.DAILY -> from.plusDays(1)
        RecurringFrequency.WEEKLY -> from.plusWeeks(1) // +7d preserves the weekday
        RecurringFrequency.MONTHLY -> {
            val ym = java.time.YearMonth.from(from).plusMonths(1)
            ym.atDay((dayOfMonth ?: from.dayOfMonth).coerceIn(1, ym.lengthOfMonth()))
        }
    }
}

/**
 * Cadence for a [RecurringTransactionEntity]. Stored as its enum name (TEXT) by
 * Room's built-in enum support (no converter needed) and as a string by
 * kotlinx.serialization for the backup.
 */
@Serializable
enum class RecurringFrequency {
    DAILY,
    WEEKLY,
    MONTHLY;

    // Note: a yearly cadence needs a month anchor we don't model yet, so it's
    // intentionally not offered. Add it back with a month field when supported.

    /**
     * The next occurrence strictly after [from]. The monthly cadence uses real
     * calendar arithmetic (`plusMonths` already clamps to the last valid day of
     * a shorter target month, so a day-31 template lands on Feb 28/29 rather
     * than throwing). [nextDueAfter] re-anchors monthly to dayOfMonth.
     */
    fun advance(from: LocalDate): LocalDate = when (this) {
        DAILY -> from.plusDays(1)
        WEEKLY -> from.plusWeeks(1)
        MONTHLY -> from.plusMonths(1)
    }
}
