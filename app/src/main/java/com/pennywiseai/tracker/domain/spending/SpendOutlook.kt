package com.pennywiseai.tracker.domain.spending

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
        const val USUAL_PERIODS = CycleAverages.CYCLES

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
            val usual = CycleAverages.mean(previousPeriods)
            return SpendForecast(currency, spent, projected, periodEnd, (total - elapsed).toInt(), usual)
        }
    }
}

/**
 * A payment due on [dueDate], in its own currency. A card bill ([isCardBill]) past
 * its due date is still owed, so it stays in the window ([SafeToSpend.billsInWindow]).
 */
data class Bill(
    val name: String,
    val amount: Money,
    val dueDate: LocalDate,
    val isCardBill: Boolean = false,
    /** What the bill is for, so a screen can open it; null when not known. */
    val source: BillSource? = null,
)

/** Where a [Bill] comes from. */
sealed interface BillSource {
    data object Subscription : BillSource
    data object Recurring : BillSource
    data class Card(val bankName: String, val accountLast4: String) : BillSource
}

/**
 * Money free to spend in one currency until the period ends: spendable balance
 * minus the bills due before then and the [goalReserve] the user asked to keep,
 * and never more than [budgetLeft]. [amount] can be negative (bills exceed balance).
 */
data class SafeToSpend(
    val currency: String,
    val balance: BigDecimal,
    val bills: BigDecimal,
    /** Days left in the window, today included. */
    val days: Int,
    /** What's left in the user's overall budget, never below 0; null when there's none. */
    val budgetLeft: BigDecimal? = null,
    /** Set aside for goals; zero unless the user opted in. */
    val goalReserve: BigDecimal = BigDecimal.ZERO,
) {
    private val afterReserves: BigDecimal get() = balance - bills - goalReserve

    /** True when the budget, not the balance, sets [amount]. */
    val isCapped: Boolean get() = budgetLeft != null && budgetLeft < afterReserves

    val amount: BigDecimal get() = budgetLeft?.let { minOf(it, afterReserves) } ?: afterReserves

    /** Even daily share of [amount]; null when nothing is left. Rounded down, so it never overstates. */
    val perDay: BigDecimal?
        get() = amount.takeIf { it.signum() > 0 }?.divide(BigDecimal(days), 0, RoundingMode.DOWN)

    companion object {
        /**
         * One result per currency: amounts are only ever netted within a currency.
         * Bills outside today..[periodEnd] are ignored, except overdue card bills.
         * [budgetLeft] caps only the result in its own currency; a negative one caps
         * at 0. A [goalReserves] amount comes off its own currency's figure, and only
         * where there's money or a bill in it: a goal alone doesn't open a line.
         * Card purchases aren't here at all: they're inside the card's bill.
         */
        fun calculate(
            balances: List<Money>,
            bills: List<Bill>,
            today: LocalDate,
            periodEnd: LocalDate,
            budgetLeft: Money? = null,
            goalReserves: List<Money> = emptyList(),
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
                    budgetLeft = budgetLeft?.takeIf { it.currency == c }?.amount?.max(BigDecimal.ZERO),
                    goalReserve = goalReserves.filter { it.currency == c }.fold(BigDecimal.ZERO) { a, m -> a + m.amount },
                )
            }
        }

        /**
         * Unified mode: [money] in [display], or as it is when there's no rate
         * ([convert] returns null), so it stays a line of its own rather than being
         * added in (#670). A converted amount is an estimate at today's rate, so it's
         * whole units, like the per-day figure: no paise from a rate in the total.
         */
        suspend fun inDisplay(
            money: Money,
            display: String,
            convert: suspend (amount: BigDecimal, from: String, to: String) -> BigDecimal?,
        ): Money =
            if (money.currency == display) money
            else convert(money.amount, money.currency, display)?.let { Money(it.setScale(0, RoundingMode.HALF_UP), display) } ?: money

        /** Bills due today..[periodEnd], and overdue card bills: those are still owed. */
        fun billsInWindow(bills: List<Bill>, today: LocalDate, periodEnd: LocalDate): List<Bill> =
            bills.filter { (it.isCardBill || !it.dueDate.isBefore(today)) && !it.dueDate.isAfter(periodEnd) }
    }
}
