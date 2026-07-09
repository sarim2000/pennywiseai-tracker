package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.BudgetDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.dao.TransactionSplitDao
import com.pennywiseai.tracker.data.database.entity.BudgetCategoryEntity
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.domain.model.BudgetCycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao,
    private val transactionSplitDao: TransactionSplitDao
) {
    fun getActiveBudgets(): Flow<List<BudgetEntity>> =
        budgetDao.getActiveBudgets()

    fun getAllBudgets(): Flow<List<BudgetEntity>> =
        budgetDao.getAllBudgets()

    fun getCurrentBudgets(): Flow<List<BudgetEntity>> =
        budgetDao.getCurrentBudgets(LocalDate.now())

    suspend fun getBudgetById(id: Long): BudgetEntity? =
        budgetDao.getBudgetById(id)

    fun getBudgetByIdFlow(id: Long): Flow<BudgetEntity?> =
        budgetDao.getBudgetByIdFlow(id)

    fun getCategoriesForBudget(budgetId: Long): Flow<List<BudgetCategoryEntity>> =
        budgetDao.getCategoriesForBudget(budgetId)

    suspend fun getCategoryNamesForBudget(budgetId: Long): List<String> =
        budgetDao.getCategoryNamesForBudget(budgetId)

    suspend fun createBudget(
        name: String,
        limitAmount: BigDecimal,
        periodType: BudgetPeriodType,
        startDate: LocalDate,
        endDate: LocalDate,
        currency: String,
        includeAllCategories: Boolean,
        categories: List<String>,
        color: String
    ): Long {
        val now = LocalDateTime.now()
        val budget = BudgetEntity(
            name = name,
            limitAmount = limitAmount,
            periodType = periodType,
            startDate = startDate,
            endDate = endDate,
            currency = currency,
            isActive = true,
            includeAllCategories = includeAllCategories,
            color = color,
            createdAt = now,
            updatedAt = now
        )
        val budgetId = budgetDao.insertBudget(budget)

        if (!includeAllCategories && categories.isNotEmpty()) {
            val budgetCategories = categories.map { categoryName ->
                BudgetCategoryEntity(
                    budgetId = budgetId,
                    categoryName = categoryName
                )
            }
            budgetDao.insertBudgetCategories(budgetCategories)
        }

        return budgetId
    }

    suspend fun updateBudget(
        budgetId: Long,
        name: String,
        limitAmount: BigDecimal,
        periodType: BudgetPeriodType,
        startDate: LocalDate,
        endDate: LocalDate,
        currency: String,
        includeAllCategories: Boolean,
        categories: List<String>,
        color: String
    ) {
        val existingBudget = budgetDao.getBudgetById(budgetId) ?: return
        val updatedBudget = existingBudget.copy(
            name = name,
            limitAmount = limitAmount,
            periodType = periodType,
            startDate = startDate,
            endDate = endDate,
            currency = currency,
            includeAllCategories = includeAllCategories,
            color = color,
            updatedAt = LocalDateTime.now()
        )
        budgetDao.updateBudget(updatedBudget)

        // Update categories
        budgetDao.deleteCategoriesForBudget(budgetId)
        if (!includeAllCategories && categories.isNotEmpty()) {
            val budgetCategories = categories.map { categoryName ->
                BudgetCategoryEntity(
                    budgetId = budgetId,
                    categoryName = categoryName
                )
            }
            budgetDao.insertBudgetCategories(budgetCategories)
        }
    }

    suspend fun deleteBudget(budgetId: Long) {
        budgetDao.deleteBudgetById(budgetId)
    }

    suspend fun deactivateBudget(budgetId: Long) {
        budgetDao.deactivateBudget(budgetId)
    }

    /**
     * Calculate spending for a budget.
     * This queries all EXPENSE transactions within the budget's date range and currency,
     * filtered by the budget's selected categories (or all categories if includeAllCategories is true).
     *
     * For transactions with splits, each split's amount is counted towards its respective category.
     * This ensures accurate budget tracking when a single transaction is split across multiple categories.
     */
    fun getBudgetSpending(budget: BudgetEntity): Flow<BudgetSpending> {
        // Use TransactionWithSplits to properly handle split transactions
        val transactionsWithSplitsFlow = transactionSplitDao.getTransactionsWithSplitsFiltered(
            startDate = budget.startDate.atStartOfDay(),
            endDate = budget.endDate.atTime(23, 59, 59),
            currency = budget.currency
        ).map { allTransactions ->
            // Filter to only include EXPENSE transactions, excluding loan repayments
            // and transactions the user excluded from analytics (#451).
            allTransactions.filter {
                it.transaction.transactionType == TransactionType.EXPENSE &&
                    it.transaction.loanId == null &&
                    !it.transaction.excludedFromAnalytics
            }
        }

        val categoriesFlow = if (budget.includeAllCategories) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            budgetDao.getCategoriesForBudget(budget.id)
        }

        return combine(transactionsWithSplitsFlow, categoriesFlow) { transactionsWithSplits, budgetCategories ->
            val categoryNames = budgetCategories.map { it.categoryName }.toSet()

            // Build category amounts considering splits
            val categoryAmounts = mutableMapOf<String, BigDecimal>()
            var totalSpent = BigDecimal.ZERO
            var transactionCount = 0

            transactionsWithSplits.forEach { txWithSplits ->
                // Get amounts by category (handles both split and non-split transactions)
                val amountsByCategory = txWithSplits.getAmountByCategory()

                amountsByCategory.forEach { (category, amount) ->
                    val categoryName = category.ifEmpty { "Others" }

                    // Check if this category is included in the budget
                    val includeThisCategory = budget.includeAllCategories || categoryName in categoryNames

                    if (includeThisCategory) {
                        totalSpent += amount
                        categoryAmounts[categoryName] = (categoryAmounts[categoryName] ?: BigDecimal.ZERO) + amount
                    }
                }

                // Count the transaction if any portion is included in the budget
                val hasIncludedCategory = if (budget.includeAllCategories) {
                    true
                } else {
                    amountsByCategory.keys.any { cat ->
                        val categoryName = cat.ifEmpty { "Others" }
                        categoryName in categoryNames
                    }
                }
                if (hasIncludedCategory) {
                    transactionCount++
                }
            }

            val remaining = budget.limitAmount - totalSpent

            val percentageUsed = if (budget.limitAmount > BigDecimal.ZERO) {
                (totalSpent.toFloat() / budget.limitAmount.toFloat() * 100f).coerceIn(0f, 100f)
            } else {
                0f
            }

            // Sort category breakdown by amount
            val categoryBreakdown = categoryAmounts
                .toList()
                .sortedByDescending { it.second }
                .toMap()

            BudgetSpending(
                totalSpent = totalSpent,
                remaining = remaining,
                percentageUsed = percentageUsed,
                categoryBreakdown = categoryBreakdown,
                transactionCount = transactionCount
            )
        }
    }

    /**
     * Calculate the daily spending allowance for a budget.
     * This is: (remaining budget) / (days remaining in budget period)
     */
    fun calculateDailyAllowance(budget: BudgetEntity, spent: BigDecimal): BigDecimal {
        val today = LocalDate.now()
        val daysRemaining = ChronoUnit.DAYS.between(today, budget.endDate).toInt() + 1
        val remaining = budget.limitAmount - spent

        return if (daysRemaining > 0 && remaining > BigDecimal.ZERO) {
            remaining.divide(BigDecimal(daysRemaining), 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }
    }

    /**
     * Calculate days remaining in the budget period.
     */
    fun getDaysRemaining(budget: BudgetEntity): Int {
        val today = LocalDate.now()
        return (ChronoUnit.DAYS.between(today, budget.endDate).toInt() + 1).coerceAtLeast(0)
    }

    /**
     * Calculate the total days in the budget period.
     */
    fun getTotalDays(budget: BudgetEntity): Int {
        return (ChronoUnit.DAYS.between(budget.startDate, budget.endDate).toInt() + 1).coerceAtLeast(1)
    }

    /**
     * Calculate the progress through the budget period (0.0 to 1.0).
     */
    fun getTimeProgress(budget: BudgetEntity): Float {
        val today = LocalDate.now()
        val totalDays = getTotalDays(budget)
        val daysPassed = ChronoUnit.DAYS.between(budget.startDate, today).toInt() + 1
        return (daysPassed.toFloat() / totalDays.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Calculate start and end dates based on period type.
     *
     * When [customStartDate] is provided, it becomes the budget's start — the
     * global [startDay] preference is ignored and the window is anchored to the
     * user's chosen date. This lets a budget run on a non-cycle cadence
     * (e.g. a "vacation" budget that starts on Aug 15 regardless of the
     * user's 25th-of-month cycle).
     */
    fun calculatePeriodDates(
        periodType: BudgetPeriodType,
        customStartDate: LocalDate? = null,
        startDay: Int = BudgetCycle.DEFAULT_START_DAY
    ): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now()
        // When the caller pins a start date, ignore the global cycle and
        // anchor the window to that date. Used by the BudgetGroup edit screen
        // so each budget can have its own start regardless of the cycle pref.
        if (customStartDate != null) {
            return customStartDate to endDateFor(customStartDate, periodType)
        }
        return when (periodType) {
            BudgetPeriodType.WEEKLY -> {
                val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val endOfWeek = startOfWeek.plusDays(6)
                startOfWeek to endOfWeek
            }
            BudgetPeriodType.MONTHLY -> {
                val (start, end) = BudgetCycle.currentCycle(today, startDay)
                start to end
            }
            BudgetPeriodType.CUSTOM -> {
                // Legacy path: caller didn't pin a date, so fall back to today.
                // The BudgetGroup edit screen always pins a date now, so this
                // branch is only reached by the older BudgetRepository callers.
                today to today.plusMonths(1)
            }
        }
    }

    companion object {
        /**
         * Calculate start and end dates based on period type. Companion-object
         * mirror of the instance method so the BudgetGroup repo can call it
         * without an instance (and without dragging in a whole new dependency).
         *
         * [today] is a test seam — production callers should leave it default
         * and the call reads `LocalDate.now()`; tests pin a fixed date to
         * keep the assertions deterministic.
         */
        fun calculatePeriodDates(
            periodType: BudgetPeriodType,
            customStartDate: LocalDate? = null,
            startDay: Int = BudgetCycle.DEFAULT_START_DAY,
            today: LocalDate = LocalDate.now()
        ): Pair<LocalDate, LocalDate> {
            if (customStartDate != null) {
                return customStartDate to endDateFor(customStartDate, periodType)
            }
            return when (periodType) {
                BudgetPeriodType.WEEKLY -> {
                    val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val endOfWeek = startOfWeek.plusDays(6)
                    startOfWeek to endOfWeek
                }
                BudgetPeriodType.MONTHLY -> {
                    val (start, end) = BudgetCycle.currentCycle(today, startDay)
                    start to end
                }
                BudgetPeriodType.CUSTOM -> {
                    today to today.plusMonths(1)
                }
            }
        }

        /**
         * Derive the [endDate] for a budget that starts on [start] and runs for
         * one [periodType]. Kept small and side-effect-free so the edit screen
         * can call it on every keystroke of the start-date picker to re-render
         * the read-only "Ends on …" label. Lives on the companion object so
         * [BudgetGroupRepository] can call it without an instance.
         */
        fun endDateFor(start: LocalDate, periodType: BudgetPeriodType): LocalDate = when (periodType) {
            BudgetPeriodType.WEEKLY -> start.plusDays(6)
            BudgetPeriodType.MONTHLY -> start.plusMonths(1).minusDays(1)
            BudgetPeriodType.CUSTOM -> start.plusDays(29)
        }
    }

    /**
     * Renew a budget for the next period (for recurring budgets).
     */
    suspend fun renewBudget(budget: BudgetEntity, startDay: Int = BudgetCycle.DEFAULT_START_DAY) {
        val (newStartDate, newEndDate) = when (budget.periodType) {
            BudgetPeriodType.WEEKLY -> {
                budget.endDate.plusDays(1) to budget.endDate.plusDays(7)
            }
            BudgetPeriodType.MONTHLY -> {
                val newStart = BudgetCycle.nextCycleStart(budget.endDate.plusDays(1), startDay)
                val newEnd = BudgetCycle.nextCycleStart(newStart, startDay).minusDays(1)
                newStart to newEnd
            }
            BudgetPeriodType.CUSTOM -> {
                // Custom budgets don't auto-renew
                return
            }
        }

        val updatedBudget = budget.copy(
            startDate = newStartDate,
            endDate = newEndDate,
            updatedAt = LocalDateTime.now()
        )
        budgetDao.updateBudget(updatedBudget)
    }
}

/**
 * Data class representing the spending data for a budget.
 */
data class BudgetSpending(
    val totalSpent: BigDecimal,
    val remaining: BigDecimal,
    val percentageUsed: Float,
    val categoryBreakdown: Map<String, BigDecimal>,
    val transactionCount: Int
)
