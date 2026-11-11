package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.expandWithChildren
import com.pennywiseai.tracker.data.ledger.forTotals
import com.pennywiseai.tracker.data.model.*
import com.pennywiseai.tracker.data.preferences.CurrencyPreferences
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** The figures the AI chat reads: this month's totals and one category's spend. */
@Singleton
class AiContextRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val transactionSplitDao: com.pennywiseai.tracker.data.database.dao.TransactionSplitDao,
    private val userPreferencesRepository: CurrencyPreferences,
    private val currencyConversionService: CurrencyConversionService,
    private val loanRepository: LoanRepository,
    private val categoryDao: com.pennywiseai.tracker.data.database.dao.CategoryDao
) {

    /** This month so far, in the base currency: all the chat's prompt and summary tool need. */
    suspend fun getMonthSummary(): MonthSummary =
        getMonthSummary(LocalDate.now(), userPreferencesRepository.baseCurrency.first())

    private suspend fun getMonthSummary(currentDate: LocalDate, baseCurrency: String): MonthSummary {
        val yearMonth = YearMonth.from(currentDate)
        val startOfMonth = yearMonth.atDay(1)
        val endOfMonth = yearMonth.atEndOfMonth()

        // Get all transactions for current month
        val transactions = transactionDao.getTransactionsBetweenDatesList(
            startOfMonth.atStartOfDay(),
            endOfMonth.atTime(23, 59, 59)
        ).forTotals()  // exclude one-off purchases (#451) and loan-linked transactions from AI summary

        var totalIncome = BigDecimal.ZERO
        var totalExpense = BigDecimal.ZERO
        val transactionCount = transactions.size

        // Process in a single pass
        transactions.forEach { transaction ->
            // Skip unconvertible amounts rather than counting face value (#670).
            val convertedAmount = currencyConversionService.convertAmountOrNull(
                amount = transaction.amount,
                fromCurrency = transaction.currency,
                toCurrency = baseCurrency
            ) ?: return@forEach
            when (transaction.transactionType) {
                TransactionType.INCOME -> totalIncome = totalIncome.add(convertedAmount)
                TransactionType.EXPENSE -> totalExpense = totalExpense.add(convertedAmount)
                TransactionType.CREDIT -> totalExpense = totalExpense.add(convertedAmount) // Credit counts as expense
                TransactionType.TRANSFER -> {} // Transfers don't affect income/expense totals
                TransactionType.INVESTMENT -> {} // Investments are asset reallocation, not expenses
            }
        }

        return MonthSummary(
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            transactionCount = transactionCount,
            daysInMonth = yearMonth.lengthOfMonth(),
            currentDay = currentDate.dayOfMonth
        )
    }

    /** This month's spend in one category, for the chat's lookup tool (#170). Null = nothing found. */
    suspend fun getCategorySpending(category: String): CategorySpending? {
        val baseCurrency = userPreferencesRepository.baseCurrency.first()
        return getTopCategories(LocalDate.now(), baseCurrency, limit = Int.MAX_VALUE)
            .firstOrNull { it.category.equals(category, ignoreCase = true) }
    }

    /** [SpendingComparison] on the saved transactions, as of now. Also for one-tap questions that skip the model (M8). */
    suspend fun compareSpending(period: SpendingComparison.Period, category: String?, creditAsExpense: Boolean): SpendingComparison.Result {
        val windows = SpendingComparison.windows(
            period, java.time.LocalDateTime.now(), java.time.temporal.WeekFields.of(java.util.Locale.getDefault()).firstDayOfWeek
        )
        val (current, previous) = windows.toList().map { transactionSplitDao.getTransactionsWithSplitsAllCurrencies(it.start, it.end).first() }
        // Forgiven loan balances count as spent, as on Home (#938); a category comparison leaves them out.
        val (currentLoss, previousLoss) = if (category == null) settlementLoss(windows.first) to settlementLoss(windows.second) else emptyMap<String, BigDecimal>() to emptyMap()
        // A parent counts its children, as the category filters do. The model's casing isn't the saved name's.
        val categories = category?.let { asked ->
            val all = categoryDao.getAllCategoriesList()
            all.expandWithChildren(listOf(all.firstOrNull { it.name.equals(asked, ignoreCase = true) }?.name ?: asked))
        }
        return SpendingComparison.compare(windows, current, previous, categories, currentLoss, previousLoss, creditAsExpense)
    }

    /** Lent balances forgiven in [window], per currency: Home adds these to "Spent" (#938). */
    suspend fun settlementLoss(window: SpendingComparison.Window): Map<String, BigDecimal> =
        loanRepository.settlementLossByCurrency(loanRepository.getLentLoansSettledInPeriod(window.start, window.end).first()) { true }

    /** This month so far, every non-deleted transaction with its splits: for M8's one-tap questions. */
    suspend fun monthSoFar(): Pair<SpendingComparison.Window, List<com.pennywiseai.tracker.data.database.entity.TransactionWithSplits>> {
        val window = SpendingComparison.windows(SpendingComparison.Period.MONTH, java.time.LocalDateTime.now(), java.time.DayOfWeek.MONDAY).first
        return window to transactionSplitDao.getTransactionsWithSplitsAllCurrencies(window.start, window.end).first()
    }

    private suspend fun getTopCategories(currentDate: LocalDate, baseCurrency: String, limit: Int = 5): List<CategorySpending> {
        val yearMonth = YearMonth.from(currentDate)
        val startOfMonth = yearMonth.atDay(1)
        val endOfMonth = yearMonth.atEndOfMonth()

        val transactions = transactionDao.getTransactionsBetweenDatesList(
            startOfMonth.atStartOfDay(),
            endOfMonth.atTime(23, 59, 59)
        ).forTotals()  // exclude one-off purchases (#451) and loan-linked transactions from AI summary

        // Group by category and calculate spending
        val categoryMap = mutableMapOf<String, MutableList<BigDecimal>>()
        var totalExpense = BigDecimal.ZERO

        transactions
            // Credit counts as expense, as in getMonthSummary: a category can't total less than its spends.
            .filter { it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.CREDIT }
            .forEach { transaction ->
                val category = transaction.category ?: "Others"
                val convertedAmount = currencyConversionService.convertAmountOrNull(
                    amount = transaction.amount,
                    fromCurrency = transaction.currency,
                    toCurrency = baseCurrency
                ) ?: return@forEach // skip unconvertible rather than face-value (#670)
                categoryMap.getOrPut(category) { mutableListOf() }.add(convertedAmount)
                totalExpense = totalExpense.add(convertedAmount)
            }

        return categoryMap.map { (category, amounts) ->
            val categoryTotal = amounts.reduce { acc, amount -> acc.add(amount) }
            val percentage = if (totalExpense > BigDecimal.ZERO) {
                categoryTotal.divide(totalExpense, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal(100))
                    .toFloat()
            } else 0f

            CategorySpending(
                category = category,
                amount = categoryTotal,
                percentage = percentage,
                transactionCount = amounts.size
            )
        }
            .sortedByDescending { it.amount }
            .take(limit)
    }
}
