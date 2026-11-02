package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Pure matching rules for "is this charge the same recurring bill?" — shared by
 * the SMS→subscription matcher and the repeat-charge detector so both agree.
 */
object RecurringCharge {

    /** Charges that can pay a bill. Refunds (INCOME) and transfers never do. */
    val SPEND_TYPES = setOf(TransactionType.EXPENSE, TransactionType.CREDIT)

    /** Lower-cased letters/digits only, so "NETFLIX" and "Netflix.com" compare equal. */
    fun merchantKey(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    /**
     * Same key. No prefix match: "swiggy" would swallow "swiggyone", so a food
     * order could pay or reprice a Swiggy One bill.
     */
    fun sameMerchant(a: String, b: String): Boolean {
        val key = merchantKey(a)
        return key.isNotEmpty() && key == merchantKey(b)
    }

    /** Within 5% of the larger amount — symmetric, so match(a, b) == match(b, a). */
    fun sameAmount(a: BigDecimal, b: BigDecimal): Boolean =
        a.subtract(b).abs() <= a.max(b).multiply(BigDecimal("0.05"))

    /**
     * The billing cycle implied by the days between two charges, or null when the
     * gap isn't a cadence. Values match [SubscriptionEntity.billingCycle] strings.
     */
    fun cycleForGap(days: Long): String? = when (days) {
        in 6L..8L -> "Weekly"
        in 26L..35L -> "Monthly"
        in 85L..97L -> "Quarterly"
        in 175L..190L -> "Semi-Annual"
        in 350L..380L -> "Annual"
        else -> null
    }

    /**
     * A charge can pay the cycle due [dueDate] from [DUE_WINDOW_DAYS] before it
     * on. A same-amount purchase earlier in the cycle doesn't; one any time
     * after does, so a bill whose schedule fell behind still catches up.
     */
    fun canPayCycle(dueDate: LocalDate?, chargeDate: LocalDate): Boolean =
        dueDate == null || !chargeDate.isBefore(dueDate.minusDays(DUE_WINDOW_DAYS))

    /**
     * The bill came due at a new price: the charge lands within 5 days of
     * [dueDate] at between half and double [billedAmount]. A charge at the same
     * amount is a plain match ([sameAmount]), not a price change.
     */
    fun isPriceChange(
        billedAmount: BigDecimal,
        dueDate: LocalDate?,
        chargeAmount: BigDecimal,
        chargeDate: LocalDate
    ): Boolean {
        if (dueDate == null || sameAmount(billedAmount, chargeAmount)) return false
        if (Math.abs(ChronoUnit.DAYS.between(dueDate, chargeDate)) > DUE_WINDOW_DAYS) return false
        return chargeAmount >= billedAmount.multiply(BigDecimal("0.5")) &&
            chargeAmount <= billedAmount.multiply(BigDecimal("2"))
    }

    /**
     * An in-app "mark paid" of the cycle due [scheduled] repeats the payment made
     * on [lastPaid]: a full cycle hasn't passed since it ([cycleAfterLastPaid]),
     * and either the cycle isn't due within [EARLY_PAY_DAYS] or that payment was
     * under [EARLY_PAY_DAYS] ago. The due-soon check lets a bill paid late last
     * cycle still be paid on time this one; the paid-just-now check stops a
     * second tap after a late payment that left the next cycle due soon.
     */
    fun isRetap(lastPaid: LocalDate, cycleAfterLastPaid: LocalDate, scheduled: LocalDate, today: LocalDate): Boolean =
        today.isBefore(cycleAfterLastPaid) &&
            (today.isBefore(scheduled.minusDays(EARLY_PAY_DAYS)) || today.isBefore(lastPaid.plusDays(EARLY_PAY_DAYS)))

    const val EARLY_PAY_DAYS = 3L
    const val DUE_WINDOW_DAYS = 5L
}
