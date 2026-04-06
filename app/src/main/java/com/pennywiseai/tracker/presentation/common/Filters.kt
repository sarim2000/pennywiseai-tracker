package com.pennywiseai.tracker.presentation.common

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
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

/**
 * Filters transactions by the selected profile.
 *
 * A transaction's effective profile is:
 *   - [TransactionEntity.profileId] if explicitly set
 *   - otherwise inherited from the account it belongs to (looked up via [profileAccountKeys])
 *
 * @param selectedProfileId null means "All profiles" (no filtering)
 * @param profileAccountKeys map of profileId → set of "bankName_accountLast4" keys
 */
fun filterTransactionsByProfile(
    transactions: List<TransactionEntity>,
    selectedProfileId: Long?,
    profileAccountKeys: Map<Long, Set<String>>
): List<TransactionEntity> {
    if (selectedProfileId == null) return transactions
    return transactions.filter { tx ->
        val effectiveProfileId = tx.profileId ?: run {
            if (tx.bankName != null && tx.accountNumber != null) {
                val key = "${tx.bankName}_${tx.accountNumber}"
                profileAccountKeys.entries.firstOrNull { (_, keys) -> keys.contains(key) }?.key
            } else null
        }
        effectiveProfileId == selectedProfileId
    }
}

/**
 * Builds a map of profileId → set of "bankName_accountLast4" keys from account balances.
 */
fun buildProfileAccountKeys(accounts: List<AccountBalanceEntity>): Map<Long, Set<String>> {
    return accounts.groupBy { it.profileId }
        .mapValues { (_, accs) -> accs.map { "${it.bankName}_${it.accountLast4}" }.toSet() }
}

/**
 * Filters account balances by profile.
 *
 * @param selectedProfileId null means "All profiles" (no filtering)
 */
fun filterAccountsByProfile(
    accounts: List<AccountBalanceEntity>,
    hiddenAccounts: Set<String>,
    selectedProfileId: Long?
): List<AccountBalanceEntity> {
    return accounts.filter { account ->
        val key = "${account.bankName}_${account.accountLast4}"
        !hiddenAccounts.contains(key) &&
            (selectedProfileId == null || account.profileId == selectedProfileId)
    }
}
