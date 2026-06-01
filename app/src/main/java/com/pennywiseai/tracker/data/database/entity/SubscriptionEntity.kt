package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    @ColumnInfo(name = "merchant_name")
    val merchantName: String,
    
    @ColumnInfo(name = "amount")
    val amount: BigDecimal,
    
    @ColumnInfo(name = "next_payment_date")
    val nextPaymentDate: LocalDate?,
    
    @ColumnInfo(name = "state")
    val state: SubscriptionState = SubscriptionState.ACTIVE,
    
    @ColumnInfo(name = "bank_name")
    val bankName: String? = null,
    
    @ColumnInfo(name = "umn")
    val umn: String? = null, // Unique Mandate Number for E-Mandates
    
    @ColumnInfo(name = "category")
    val category: String? = null,
    
    @ColumnInfo(name = "sms_body")
    val smsBody: String? = null,
    
    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "currency", defaultValue = "INR")
    val currency: String = "INR",

    /**
     * Whether this recurring entry is money OUT (an EXPENSE — Netflix, EMI,
     * mandate debit) or money IN (an INCOME — wallet top-up, allowance,
     * salary). Income autopay (#371) gets phantom-created automatically when
     * `nextPaymentDate` rolls past today; expense autopay continues to be
     * matched against incoming bank-debit SMS.
     */
    @ColumnInfo(name = "direction", defaultValue = "EXPENSE")
    val direction: SubscriptionDirection = SubscriptionDirection.EXPENSE,

    /**
     * User-chosen recurrence cadence as a display string ("Weekly",
     * "Monthly", "Quarterly", "Semi-Annual", "Annual"). Drives
     * [SubscriptionRepository.advanceNextPaymentDate]'s date arithmetic
     * and the income-autopay phantom creator. Previously this was collected
     * by the form but silently dropped — a latent bug that meant every
     * subscription advanced by exactly +30 days regardless of cycle.
     */
    @ColumnInfo(name = "billing_cycle", defaultValue = "Monthly")
    val billingCycle: String = "Monthly"
)

enum class SubscriptionState {
    ACTIVE,
    HIDDEN, // Soft delete - hidden from view but kept for reactivation detection
    ENDED   // User explicitly cancelled; never auto-reactivates on new mandate SMS
}

enum class SubscriptionDirection {
    EXPENSE,  // Recurring money out (Netflix, mandates, EMIs)
    INCOME    // Recurring money in (wallet top-ups, allowance — phantom-created on schedule, #371)
}