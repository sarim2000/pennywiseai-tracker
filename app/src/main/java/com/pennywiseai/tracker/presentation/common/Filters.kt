package com.pennywiseai.tracker.presentation.common

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.preferences.BusinessFilter
import java.time.LocalDate
import java.time.YearMonth

enum class TimePeriod(val label: String) {
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    CURRENT_FY("Current FY"),
    ALL("All Time"),
    CUSTOM("Custom Range")
}

enum class TransactionTypeFilter(val label: String) {
    ALL("All"),
    INCOME("Income"),
    EXPENSE("Expense"),
    CREDIT("Credit"),
    TRANSFER("Transfer"),
    INVESTMENT("Investment")
}

fun getDateRangeForPeriod(period: TimePeriod): Pair<LocalDate, LocalDate>? {
    val today = LocalDate.now()
    return when (period) {
        TimePeriod.THIS_MONTH -> {
            val start = YearMonth.now().atDay(1)
            start to today
        }
        TimePeriod.LAST_MONTH -> {
            val lastMonth = YearMonth.now().minusMonths(1)
            val start = lastMonth.atDay(1)
            val end = lastMonth.atEndOfMonth()
            start to end
        }
        TimePeriod.CURRENT_FY -> {
            // Indian Financial Year: April 1 to March 31
            val currentYear = today.year
            val currentMonth = today.monthValue
            val fyStart = if (currentMonth >= 4) {
                LocalDate.of(currentYear, 4, 1)  // Apr 1 of current year
            } else {
                LocalDate.of(currentYear - 1, 4, 1)  // Apr 1 of previous year
            }
            fyStart to today
        }
        TimePeriod.ALL -> {
            // Use a reasonable date range for "All Time" - 10 years back to today
            val start = today.minusYears(10)
            start to today
        }
        TimePeriod.CUSTOM -> {
            // Custom range is handled separately in ViewModel
            null
        }
    }
}

fun filterTransactionsByBusiness(
    transactions: List<TransactionEntity>,
    filter: BusinessFilter,
    businessAccountKeys: Set<String>
): List<TransactionEntity> {
    if (filter == BusinessFilter.ALL) return transactions
    return transactions.filter { tx ->
        val isBusinessTx = when (tx.isBusiness) {
            true -> true
            false -> false
            null -> tx.bankName != null && tx.accountNumber != null &&
                businessAccountKeys.contains("${tx.bankName}_${tx.accountNumber}")
        }
        when (filter) {
            BusinessFilter.ALL -> true
            BusinessFilter.BUSINESS -> isBusinessTx
            BusinessFilter.PERSONAL -> !isBusinessTx
        }
    }
}