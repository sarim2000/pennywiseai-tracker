package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.utils.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Where this period's spending is heading, in one currency.
 *
 * [projected] = spent so far / days elapsed × days in period (today counts as
 * elapsed). [usual] is the average spend of the previous [USUAL_PERIODS] complete
 * periods, or null when there isn't that much history.
 */
data class SpendForecast(
    val currency: String,
    val spent: BigDecimal,
    val projected: BigDecimal,
    val periodEnd: LocalDate,
    val daysLeft: Int,
    val usual: BigDecimal?,
) {
    /** How far [projected] is above [usual]; null when not above it (or no usual). */
    val overUsual: BigDecimal?
        get() = usual?.let { projected - it }?.takeIf { it.signum() > 0 }

    /**
     * The daily spend for the rest of the period that lands exactly on [usual]:
     * only when the pace is over it, there are days left, and it isn't already spent.
     */
    val dailyToStayAtUsual: BigDecimal?
        get() {
            val u = usual ?: return null
            if (overUsual == null || daysLeft <= 0 || spent >= u) return null
            return (u - spent).divide(BigDecimal(daysLeft), 0, RoundingMode.DOWN)
        }

    companion object {
        /** Early-period pace is noise (one rent payment on day 2), so wait a week. */
        const val MIN_DAYS = 7
        const val USUAL_PERIODS = 3

        /**
         * @param previousPeriods spend of the periods before this one, most recent
         *   first; null for a period with no data at all.
         * @return null before day [MIN_DAYS] or when [today] is outside the period.
         */
        fun of(
            spent: BigDecimal,
            currency: String,
            periodStart: LocalDate,
            periodEnd: LocalDate,
            today: LocalDate,
            previousPeriods: List<BigDecimal?>,
        ): SpendForecast? {
            if (today.isBefore(periodStart) || today.isAfter(periodEnd)) return null
            val elapsed = ChronoUnit.DAYS.between(periodStart, today) + 1
            if (elapsed < MIN_DAYS) return null
            val total = ChronoUnit.DAYS.between(periodStart, periodEnd) + 1
            val projected = spent.multiply(BigDecimal(total)).divide(BigDecimal(elapsed), 0, RoundingMode.HALF_UP)
            val history = previousPeriods.take(USUAL_PERIODS)
            val usual = if (history.size == USUAL_PERIODS && history.all { it != null }) {
                history.fold(BigDecimal.ZERO) { acc, v -> acc + v!! }
                    .divide(BigDecimal(USUAL_PERIODS), 0, RoundingMode.HALF_UP)
            } else null
            return SpendForecast(currency, spent, projected, periodEnd, (total - elapsed).toInt(), usual)
        }
    }
}

/** A payment due on [dueDate], in its own currency. */
data class Bill(val name: String, val amount: Money, val dueDate: LocalDate)

/**
 * Money free to spend in one currency until the period ends: spendable balance
 * minus the bills due before then. [amount] can be negative (bills exceed balance).
 */
data class SafeToSpend(
    val currency: String,
    val balance: BigDecimal,
    val bills: BigDecimal,
    /** Days left in the window, today included. */
    val days: Int,
) {
    val amount: BigDecimal get() = balance - bills

    /** Even daily share of [amount]; null when nothing is left. Rounded down, so it never overstates. */
    val perDay: BigDecimal?
        get() = amount.takeIf { it.signum() > 0 }?.divide(BigDecimal(days), 0, RoundingMode.DOWN)

    companion object {
        /**
         * One result per currency: amounts are only ever netted within a currency.
         * Bills outside today..[periodEnd] are ignored.
         */
        fun calculate(
            balances: List<Money>,
            bills: List<Bill>,
            today: LocalDate,
            periodEnd: LocalDate,
        ): List<SafeToSpend> {
            val days = (ChronoUnit.DAYS.between(today, periodEnd) + 1).toInt().coerceAtLeast(1)
            val due = billsInWindow(bills, today, periodEnd)
            val currencies = (balances.map { it.currency } + due.map { it.amount.currency }).distinct()
            return currencies.map { c ->
                SafeToSpend(
                    currency = c,
                    balance = balances.filter { it.currency == c }.fold(BigDecimal.ZERO) { a, m -> a + m.amount },
                    bills = due.filter { it.amount.currency == c }.fold(BigDecimal.ZERO) { a, b -> a + b.amount.amount },
                    days = days,
                )
            }
        }

        fun billsInWindow(bills: List<Bill>, today: LocalDate, periodEnd: LocalDate): List<Bill> =
            bills.filter { !it.dueDate.isBefore(today) && !it.dueDate.isAfter(periodEnd) }

        /**
         * Bills from active expense subscriptions (EMIs tracked as subscriptions
         * included). Only the next payment counts; a weekly one due twice in the
         * window is counted once.
         */
        // ponytail: next payment only; expand recurrences if weekly bills turn out to matter.
        fun billsFrom(subscriptions: List<SubscriptionEntity>): List<Bill> =
            subscriptions
                .filter { it.state == SubscriptionState.ACTIVE && it.direction == SubscriptionDirection.EXPENSE }
                .mapNotNull { s -> s.nextPaymentDate?.let { Bill(s.merchantName, Money(s.amount, s.currency), it) } }
    }
}
