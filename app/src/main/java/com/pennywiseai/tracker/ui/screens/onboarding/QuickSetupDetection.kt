package com.pennywiseai.tracker.ui.screens.onboarding

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** A charge that repeated monthly in the scanned SMS — offered as a possible subscription. */
data class RecurringCandidate(
    val name: String,
    val amount: BigDecimal,
    val currency: String,
    val category: String,
    val nextPaymentDate: LocalDate
)

/**
 * The day of the month the salary lands, from the scanned income: the biggest credit of
 * each month (only [salaryCategory] credits when there are any), in the most common
 * income currency. Null unless 2+ months agree within a few days.
 */
// ponytail: naive heuristic — a salary that straddles the month end (30th/1st) isn't detected;
// the user picks the day by hand then.
internal fun detectPayday(transactions: List<TransactionEntity>, salaryCategory: String): Int? {
    val incomes = transactions.filter { !it.isDeleted && it.transactionType == TransactionType.INCOME }
    val salary = incomes.filter { it.category == salaryCategory }.ifEmpty { incomes }
    val currency = salary.groupingBy { it.currency }.eachCount().maxByOrNull { it.value }?.key ?: return null
    val days = salary.filter { it.currency == currency }
        .groupBy { YearMonth.from(it.dateTime) }
        .values.map { month -> month.maxBy { it.amount }.dateTime.dayOfMonth }
    return days.takeIf { it.size >= 2 && it.max() - it.min() <= PAYDAY_SPREAD_DAYS }?.min()
}

/**
 * Merchants charged about monthly at a near-constant amount, still active as of [today],
 * that aren't already in [knownNames] (lowercase). Biggest first.
 */
internal fun detectRecurring(
    transactions: List<TransactionEntity>,
    knownNames: Set<String>,
    today: LocalDate
): List<RecurringCandidate> =
    transactions
        .filter {
            !it.isDeleted && it.merchantName.isNotBlank() &&
                (it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.CREDIT)
        }
        .groupBy { it.merchantName.trim().lowercase() to it.currency }
        .filterKeys { it.first !in knownNames }
        .values.mapNotNull { charges ->
            val sorted = charges.sortedBy { it.dateTime }
            val last = sorted.last()
            val lastDate = last.dateTime.toLocalDate()
            val monthly = sorted.size >= 2 && sorted.zipWithNext { a, b ->
                ChronoUnit.DAYS.between(a.dateTime.toLocalDate(), b.dateTime.toLocalDate())
            }.all { it in MONTHLY_GAP_DAYS }
            val steady = sorted.all { (it.amount - last.amount).abs() <= last.amount.multiply(AMOUNT_TOLERANCE) }
            val active = ChronoUnit.DAYS.between(lastDate, today) <= MONTHLY_GAP_DAYS.last
            if (!monthly || !steady || !active) return@mapNotNull null
            var next = lastDate.plusMonths(1)
            while (next.isBefore(today)) next = next.plusMonths(1)
            RecurringCandidate(last.merchantName.trim(), last.amount, last.currency, last.category, next)
        }
        .sortedByDescending { it.amount }
        .take(MAX_CANDIDATES)

private const val PAYDAY_SPREAD_DAYS = 5
private val MONTHLY_GAP_DAYS = 26L..35L
private val AMOUNT_TOLERANCE = BigDecimal("0.1")
private const val MAX_CANDIDATES = 8
