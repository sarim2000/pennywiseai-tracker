package com.pennywiseai.tracker.presentation.home

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import java.time.YearMonth

/**
 * The day of the month the salary lands, from the scanned income: the biggest credit of
 * each month (only [salaryCategory] credits when there are any), in the most common
 * income currency. Null unless 2+ months agree within a few days.
 */
// ponytail: naive heuristic — a salary that straddles the month end (30th/1st) isn't detected;
// the user sets the day in Settings then.
internal fun detectPayday(transactions: List<TransactionEntity>, salaryCategory: String): Int? {
    val incomes = transactions.filter { !it.isDeleted && it.transactionType == TransactionType.INCOME }
    val salary = incomes.filter { it.category == salaryCategory }.ifEmpty { incomes }
    val currency = salary.groupingBy { it.currency }.eachCount().maxByOrNull { it.value }?.key ?: return null
    val days = salary.filter { it.currency == currency }
        .groupBy { YearMonth.from(it.dateTime) }
        .values.map { month -> month.maxBy { it.amount }.dateTime.dayOfMonth }
    return days.takeIf { it.size >= 2 && it.max() - it.min() <= PAYDAY_SPREAD_DAYS }?.min()
}

private const val PAYDAY_SPREAD_DAYS = 5

/** How many complete months back Home looks for the salary. */
internal const val PAYDAY_HISTORY_MONTHS = 4L
