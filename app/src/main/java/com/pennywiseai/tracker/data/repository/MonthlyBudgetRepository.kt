package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.CategoryBudgetLimitDao
import com.pennywiseai.tracker.data.database.dao.TransactionSplitDao
import com.pennywiseai.tracker.data.database.entity.CategoryBudgetLimitEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

data class MonthlyBudgetSpending(
    val totalLimit: BigDecimal,
    val totalSpent: BigDecimal,
    val remaining: BigDecimal,
    val percentageUsed: Float,
    val categorySpending: List<CategorySpendingInfo>,
    val daysRemaining: Int,
    val dailyAllowance: BigDecimal,
    val totalIncome: BigDecimal = BigDecimal.ZERO,
    val netSavings: BigDecimal = BigDecimal.ZERO,
    val savingsRate: Float = 0f,
    val savingsDelta: BigDecimal? = null
)

data class CategorySpendingInfo(
    val categoryName: String,
    val spent: BigDecimal,
    val limit: BigDecimal?,
    val percentageUsed: Float?,
    val dailySpend: BigDecimal = BigDecimal.ZERO,
    val dailyAllowance: BigDecimal? = null
)

@Singleton
class MonthlyBudgetRepository @Inject constructor(
    private val categoryBudgetLimitDao: CategoryBudgetLimitDao,
    private val transactionSplitDao: TransactionSplitDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    val monthlyBudgetLimit: Flow<BigDecimal?> = userPreferencesRepository.monthlyBudgetLimit

    fun getCategoryLimits(): Flow<List<CategoryBudgetLimitEntity>> =
        categoryBudgetLimitDao.getAllLimits()

    suspend fun setMonthlyBudgetLimit(amount: BigDecimal?) {
        userPreferencesRepository.updateMonthlyBudgetLimit(amount)
    }

    suspend fun setCategoryLimit(categoryName: String, amount: BigDecimal) {
        categoryBudgetLimitDao.upsertLimit(
            CategoryBudgetLimitEntity(
                categoryName = categoryName,
                limitAmount = amount
            )
        )
    }

    suspend fun removeCategoryLimit(categoryName: String) {
        categoryBudgetLimitDao.deleteLimitForCategory(categoryName)
    }

    fun getMonthSpending(year: Int, month: Int, currency: String): Flow<MonthlyBudgetSpending> {
        val yearMonth = YearMonth.of(year, month)
        val startDate = yearMonth.atDay(1).atStartOfDay()
        val endDate = yearMonth.atEndOfMonth().atTime(23, 59, 59)

        val prevMonth = yearMonth.minusMonths(1)
        val prevStartDate = prevMonth.atDay(1).atStartOfDay()
        val prevEndDate = prevMonth.atEndOfMonth().atTime(23, 59, 59)

        val today = LocalDate.now()
        val daysElapsed = if (yearMonth == YearMonth.from(today)) {
            today.dayOfMonth
        } else {
            yearMonth.lengthOfMonth()
        }
        val daysRemaining = if (yearMonth == YearMonth.from(today)) {
            (ChronoUnit.DAYS.between(today, yearMonth.atEndOfMonth()).toInt() + 1).coerceAtLeast(0)
        } else {
            0
        }

        return combine(
            monthlyBudgetLimit,
            categoryBudgetLimitDao.getAllLimits(),
            transactionSplitDao.getTransactionsWithSplitsFiltered(startDate, endDate, currency),
            transactionSplitDao.getTransactionsWithSplitsFiltered(prevStartDate, prevEndDate, currency)
        ) { limit, categoryLimits, allTransactions, prevTransactions ->
            val totalLimit = limit ?: BigDecimal.ZERO
            val categoryLimitsMap = categoryLimits.associate { it.categoryName to it.limitAmount }

            // Separate expense and income transactions
            val expenseTransactions = allTransactions.filter {
                it.transaction.transactionType == TransactionType.EXPENSE ||
                    it.transaction.transactionType == TransactionType.CREDIT
            }
            val incomeTransactions = allTransactions.filter {
                it.transaction.transactionType == TransactionType.INCOME
            }

            // Sum income
            val totalIncome = incomeTransactions.fold(BigDecimal.ZERO) { acc, tx ->
                acc + tx.transaction.amount
            }

            // Build category spending from expense transactions
            val categoryAmounts = mutableMapOf<String, BigDecimal>()
            expenseTransactions.forEach { txWithSplits ->
                txWithSplits.getAmountByCategory().forEach { (category, amount) ->
                    val categoryName = category.ifEmpty { "Others" }
                    categoryAmounts[categoryName] = (categoryAmounts[categoryName] ?: BigDecimal.ZERO) + amount
                }
            }

            val totalSpent = categoryAmounts.values.fold(BigDecimal.ZERO) { acc, v -> acc + v }
            val remaining = totalLimit - totalSpent

            val percentageUsed = if (totalLimit > BigDecimal.ZERO) {
                (totalSpent.toFloat() / totalLimit.toFloat() * 100f).coerceAtLeast(0f)
            } else {
                0f
            }

            val dailyAllowance = if (daysRemaining > 0 && remaining > BigDecimal.ZERO) {
                remaining.divide(BigDecimal(daysRemaining), 0, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }

            // Build category spending info: categories with limits first, then others sorted by amount
            val allCategories = (categoryLimitsMap.keys + categoryAmounts.keys).distinct()
            val categorySpending = allCategories.map { categoryName ->
                val spent = categoryAmounts[categoryName] ?: BigDecimal.ZERO
                val catLimit = categoryLimitsMap[categoryName]
                val catPercentage = if (catLimit != null && catLimit > BigDecimal.ZERO) {
                    (spent.toFloat() / catLimit.toFloat() * 100f).coerceAtLeast(0f)
                } else {
                    null
                }
                val catDailySpend = if (daysElapsed > 0 && spent > BigDecimal.ZERO) {
                    spent.divide(BigDecimal(daysElapsed), 0, RoundingMode.HALF_UP)
                } else {
                    BigDecimal.ZERO
                }
                val catRemaining = if (catLimit != null) catLimit - spent else null
                val catDailyAllowance = if (catLimit != null && daysRemaining > 0 && catRemaining != null && catRemaining > BigDecimal.ZERO) {
                    catRemaining.divide(BigDecimal(daysRemaining), 0, RoundingMode.HALF_UP)
                } else {
                    null
                }
                CategorySpendingInfo(
                    categoryName = categoryName,
                    spent = spent,
                    limit = catLimit,
                    percentageUsed = catPercentage,
                    dailySpend = catDailySpend,
                    dailyAllowance = catDailyAllowance
                )
            }.sortedWith(compareByDescending<CategorySpendingInfo> { it.limit != null }.thenByDescending { it.spent })

            val netSavings = totalIncome - totalSpent
            val savingsRate = if (totalIncome > BigDecimal.ZERO) {
                (netSavings.toFloat() / totalIncome.toFloat() * 100f)
            } else {
                0f
            }

            // Calculate previous month's savings for delta
            val prevIncome = prevTransactions.filter {
                it.transaction.transactionType == TransactionType.INCOME
            }.fold(BigDecimal.ZERO) { acc, tx -> acc + tx.transaction.amount }

            val prevExpenses = prevTransactions.filter {
                it.transaction.transactionType == TransactionType.EXPENSE ||
                    it.transaction.transactionType == TransactionType.CREDIT
            }.fold(BigDecimal.ZERO) { acc, tx -> acc + tx.transaction.amount }

            val prevSavings = prevIncome - prevExpenses
            val savingsDelta = if (prevIncome > BigDecimal.ZERO || totalIncome > BigDecimal.ZERO) {
                netSavings - prevSavings
            } else {
                null
            }

            MonthlyBudgetSpending(
                totalLimit = totalLimit,
                totalSpent = totalSpent,
                remaining = remaining,
                percentageUsed = percentageUsed,
                categorySpending = categorySpending,
                daysRemaining = daysRemaining,
                dailyAllowance = dailyAllowance,
                totalIncome = totalIncome,
                netSavings = netSavings,
                savingsRate = savingsRate,
                savingsDelta = savingsDelta
            )
        }
    }

    fun getCurrentMonthSpending(currency: String): Flow<MonthlyBudgetSpending> {
        val today = LocalDate.now()
        return getMonthSpending(today.year, today.monthValue, currency)
    }

    /**
     * Get month spending across all currencies, returning raw per-currency breakdowns.
     * Used by unified currency mode to convert and aggregate spending.
     */
    fun getMonthSpendingAllCurrencies(year: Int, month: Int): Flow<MonthlyBudgetSpendingRaw> {
        val yearMonth = YearMonth.of(year, month)
        val startDate = yearMonth.atDay(1).atStartOfDay()
        val endDate = yearMonth.atEndOfMonth().atTime(23, 59, 59)

        val prevMonth = yearMonth.minusMonths(1)
        val prevStartDate = prevMonth.atDay(1).atStartOfDay()
        val prevEndDate = prevMonth.atEndOfMonth().atTime(23, 59, 59)

        val today = LocalDate.now()
        val daysElapsed = if (yearMonth == YearMonth.from(today)) {
            today.dayOfMonth
        } else {
            yearMonth.lengthOfMonth()
        }
        val daysRemaining = if (yearMonth == YearMonth.from(today)) {
            (ChronoUnit.DAYS.between(today, yearMonth.atEndOfMonth()).toInt() + 1).coerceAtLeast(0)
        } else {
            0
        }

        return combine(
            monthlyBudgetLimit,
            categoryBudgetLimitDao.getAllLimits(),
            transactionSplitDao.getTransactionsWithSplitsAllCurrencies(startDate, endDate),
            transactionSplitDao.getTransactionsWithSplitsAllCurrencies(prevStartDate, prevEndDate)
        ) { limit, categoryLimits, allTransactions, prevTransactions ->
            MonthlyBudgetSpendingRaw(
                totalLimit = limit ?: BigDecimal.ZERO,
                categoryLimits = categoryLimits,
                allTransactions = allTransactions,
                prevTransactions = prevTransactions,
                daysElapsed = daysElapsed,
                daysRemaining = daysRemaining
            )
        }
    }
}

data class MonthlyBudgetSpendingRaw(
    val totalLimit: BigDecimal,
    val categoryLimits: List<CategoryBudgetLimitEntity>,
    val allTransactions: List<com.pennywiseai.tracker.data.database.entity.TransactionWithSplits>,
    val prevTransactions: List<com.pennywiseai.tracker.data.database.entity.TransactionWithSplits>,
    val daysElapsed: Int,
    val daysRemaining: Int
)
