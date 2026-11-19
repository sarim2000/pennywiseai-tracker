package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
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

    /**
     * [date] moved [times] billing cycles on (back, when negative). Months and years
     * use calendar arithmetic, so "Monthly" lands on the same day of the month, not
     * 30 days on. An unknown cycle moves monthly, so a hand-made row never stays put.
     */
    fun advance(date: LocalDate, billingCycle: String, times: Long = 1): LocalDate =
        when (billingCycle.uppercase()) {
            "WEEKLY" -> date.plusWeeks(times)
            "MONTHLY" -> date.plusMonths(times)
            "QUARTERLY" -> date.plusMonths(3L * times)
            "SEMI-ANNUAL", "SEMI ANNUAL", "SEMIANNUAL" -> date.plusMonths(6L * times)
            "ANNUAL", "YEARLY" -> date.plusYears(times)
            else -> date.plusMonths(times)
        }

    /**
     * The next payment as the Subscriptions screen shows it: [scheduled], moved on a
     * [billingCycle] at a time while it's in the past. Each step counts from
     * [scheduled], so a bill due on the 31st comes back to the 31st after February.
     * A payment due today stays today: a paid bill has already moved on
     * ([com.pennywiseai.tracker.data.repository.SubscriptionRepository.recordCharge]),
     * so one still on today hasn't been seen paid. Anything else that names a
     * subscription's next date reads this, so the two never disagree.
     */
    fun nextPaymentShown(scheduled: LocalDate, billingCycle: String, today: LocalDate): LocalDate {
        var cycles = 0L
        var next = scheduled
        while (next.isBefore(today)) next = advance(scheduled, billingCycle, ++cycles)
        return next
    }

    /**
     * [sub] once a payment on [paidAt] has moved its schedule to [next]. A plan with
     * [SubscriptionEntity.totalPayments] counts the payment, and its last one ends
     * the plan where it stands: ENDED, so reminders and the totals drop it, with
     * the date left on the payment just made rather than a cycle that won't come.
     */
    fun afterPayment(sub: SubscriptionEntity, paidAt: LocalDate, next: LocalDate): SubscriptionEntity {
        val total = sub.totalPayments ?: return sub.copy(lastPaidAt = paidAt, nextPaymentDate = next)
        val made = sub.paymentsMade + 1
        return if (made < total) {
            sub.copy(lastPaidAt = paidAt, nextPaymentDate = next, paymentsMade = made)
        } else {
            sub.copy(lastPaidAt = paidAt, paymentsMade = made, state = SubscriptionState.ENDED)
        }
    }

    /** What's left to pay on a plan: one [SubscriptionEntity.amount] per payment left. Null for one with no end. */
    fun amountLeft(sub: SubscriptionEntity): BigDecimal? = sub.totalPayments?.let { total ->
        sub.amount.multiply(BigDecimal((total - sub.paymentsMade).coerceAtLeast(0)))
    }

    const val EARLY_PAY_DAYS = 3L
    const val DUE_WINDOW_DAYS = 5L
}
