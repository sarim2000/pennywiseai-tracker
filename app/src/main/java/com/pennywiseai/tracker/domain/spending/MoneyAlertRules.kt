package com.pennywiseai.tracker.domain.spending

import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Declared in rising order: a latched tier silences itself and anything below it. */
enum class BudgetAlertTier { WARNING, OVER }

/** When a budget or a balance is worth a notification. Pure, so every edge is testable. */
object MoneyAlertRules {

    /**
     * [BudgetAlertTier.OVER] once [spent] passes [limit]; exactly the limit is
     * used up, not exceeded. [BudgetAlertTier.WARNING] at 80% of the limit, but
     * only while under 80% of the window has gone: 82% on day 28 of 30 is on
     * plan, not news. Null outside the window or without a positive limit.
     */
    // ponytail: a budget holding rent can reach 80% on day 1. One alert per window, so left alone.
    fun budgetTier(
        spent: BigDecimal,
        limit: BigDecimal,
        windowStart: LocalDate,
        windowEnd: LocalDate,
        today: LocalDate
    ): BudgetAlertTier? {
        if (limit.signum() <= 0 || today.isBefore(windowStart) || today.isAfter(windowEnd)) return null
        if (spent > limit) return BudgetAlertTier.OVER
        val windowDays = ChronoUnit.DAYS.between(windowStart, windowEnd) + 1
        val daysElapsed = ChronoUnit.DAYS.between(windowStart, today) + 1
        val nearLimit = spent.multiply(BigDecimal(5)) >= limit.multiply(BigDecimal(4))
        val earlyInWindow = daysElapsed * 5 < windowDays * 4
        return if (nearLimit && earlyInWindow) BudgetAlertTier.WARNING else null
    }

    /**
     * The latch to store if [tier] should fire now, or null if it was already
     * told for this [window]. A new window (or a changed limit, part of the
     * [window] key) starts over; spend falling and rising again doesn't.
     */
    fun nextBudgetLatch(stored: String?, window: String, tier: BudgetAlertTier): String? {
        val told = stored
            ?.takeIf { it.substringBeforeLast(LATCH_SEPARATOR) == window }
            ?.substringAfterLast(LATCH_SEPARATOR)
            ?.let { name -> BudgetAlertTier.entries.firstOrNull { it.name == name } }
        return if (told != null && told >= tier) null else "$window$LATCH_SEPARATOR${tier.name}"
    }

    /**
     * True only on the step from above [threshold] to at or below it, so a
     * threshold set while already under it stays quiet, as does every later
     * charge while the balance stays low.
     */
    fun crossedLowBalance(before: BigDecimal?, after: BigDecimal, threshold: BigDecimal?): Boolean =
        before != null && threshold != null && before > threshold && after <= threshold

    private const val LATCH_SEPARATOR = "#"
}
