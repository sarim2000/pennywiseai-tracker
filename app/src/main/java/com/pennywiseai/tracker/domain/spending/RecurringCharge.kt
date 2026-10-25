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
     * Same keys, or one key prefixes the other — a mandate says "NETFLIX", the
     * debit SMS says "Netflix Entertainment". The 4-char floor stops "ab"
     * swallowing every merchant starting with "ab".
     */
    fun sameMerchant(a: String, b: String): Boolean {
        val ka = merchantKey(a)
        val kb = merchantKey(b)
        if (ka.isEmpty() || kb.isEmpty()) return false
        if (ka == kb) return true
        val (short, long) = if (ka.length <= kb.length) ka to kb else kb to ka
        return short.length >= 4 && long.startsWith(short)
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
        if (Math.abs(ChronoUnit.DAYS.between(dueDate, chargeDate)) > 5) return false
        return chargeAmount >= billedAmount.multiply(BigDecimal("0.5")) &&
            chargeAmount <= billedAmount.multiply(BigDecimal("2"))
    }
}
