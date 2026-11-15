package com.pennywiseai.tracker.presentation.home

import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.ledger.forTotals
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Home's spend sparkline: cumulative spend per day, never below zero. */
internal data class SpendTrend(
    /** One point per day from the cycle start through today. */
    val thisCycle: List<BigDecimal>,
    /** The previous cycle, cut at today's day-of-cycle. */
    val lastCycleSamePoint: List<BigDecimal>,
)

/**
 * The sparkline for the cycle starting [cycleStart] against the one starting
 * [previousCycleStart], from [transactions] covering both. Native mode draws only
 * [currency]; unified mode converts every row into it.
 */
internal suspend fun spendTrend(
    transactions: List<TransactionEntity>,
    cycleStart: LocalDate,
    previousCycleStart: LocalDate,
    today: LocalDate,
    currency: String,
    isUnified: Boolean,
    convert: ConvertAmount
): SpendTrend {
    // Split into current cycle and previous cycle
    val currentMonthTxs = transactions.filter { it.dateTime.toLocalDate() >= cycleStart }
    val lastMonthTxs = transactions.filter {
        val d = it.dateTime.toLocalDate()
        d >= previousCycleStart && d < cycleStart
    }

    // Net daily expense (EXPENSE adds, Refund subtracts) for both
    // months, then cumulative below clamps at zero so refunds pull the
    // sparkline endpoint in lockstep with the displayed "Spent" stat.
    val dailySums = dailyNetExpense(currentMonthTxs, isUnified, currency, convert)
    val lastMonthDailySums = dailyNetExpense(lastMonthTxs, isUnified, currency, convert)

    // Carry the running total forward unclamped (so a refund dated
    // before the first expense still counts) and only clamp the value
    // we display, so the chart endpoint matches the month-level floor
    // applied to "Spent this cycle" in breakdownByCurrency.
    val cumulativeList = mutableListOf<BigDecimal>()
    var runningNet = BigDecimal.ZERO
    var day = cycleStart
    while (!day.isAfter(today)) {
        runningNet += (dailySums[day] ?: BigDecimal.ZERO)
        cumulativeList.add(runningNet.coerceAtLeast(BigDecimal.ZERO))
        day = day.plusDays(1)
    }

    // Match the "same period" comparison the legacy code used: walk the
    // previous cycle for as many days as today's day-of-cycle, so an
    // Oct-5 vs Sep-25..Oct-4 chart isn't accidentally drawn over the
    // full previous cycle.
    val daysToInclude = ChronoUnit.DAYS.between(cycleStart, today).toInt() + 1
    val lastMonthCumulative = mutableListOf<BigDecimal>()
    var lastRunningNet = BigDecimal.ZERO
    var lastDay = previousCycleStart
    var dayCount = 0
    while (dayCount < daysToInclude && lastDay < cycleStart) {
        lastRunningNet += (lastMonthDailySums[lastDay] ?: BigDecimal.ZERO)
        lastMonthCumulative.add(lastRunningNet.coerceAtLeast(BigDecimal.ZERO))
        lastDay = lastDay.plusDays(1)
        dayCount++
    }

    return SpendTrend(thisCycle = cumulativeList, lastCycleSamePoint = lastMonthCumulative)
}

/**
 * Net daily expense map for the sparkline: EXPENSE adds, Refund (INCOME +
 * DEDUCT_SPENT) subtracts. Loan-linked transactions are excluded. Amounts are
 * converted into [selectedCurrency] when [isUnified] is true and the txn's
 * native currency differs; otherwise only txns already in [selectedCurrency]
 * are considered. The caller is responsible for clamping the cumulative at
 * zero so refunds don't make the running total go negative.
 */
private suspend fun dailyNetExpense(
    transactions: List<TransactionEntity>,
    isUnified: Boolean,
    selectedCurrency: String,
    convert: ConvertAmount
): Map<LocalDate, BigDecimal> {
    val daily = mutableMapOf<LocalDate, BigDecimal>()
    for (raw in transactions) {
        val tx = raw.forTotals() ?: continue // keep the trend line consistent with the income/spend figures (#451)
        if (!isUnified && tx.currency != selectedCurrency) continue

        val sign = when {
            tx.transactionType == TransactionType.EXPENSE -> BigDecimal.ONE
            tx.transactionType == TransactionType.INCOME &&
                tx.budgetImpactType == BudgetImpactType.DEDUCT_SPENT -> BigDecimal.ONE.negate()
            else -> continue
        }

        val amount = if (isUnified && tx.currency != selectedCurrency) {
            convert(tx.amount, tx.currency, selectedCurrency)
                ?: continue // never-rated pair — skip, don't face-value (#670)
        } else {
            tx.amount
        }
        val day = tx.dateTime.toLocalDate()
        daily[day] = (daily[day] ?: BigDecimal.ZERO) + amount.multiply(sign)
    }
    return daily
}
