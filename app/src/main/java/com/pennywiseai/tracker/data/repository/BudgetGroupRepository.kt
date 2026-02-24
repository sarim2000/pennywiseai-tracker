package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.BudgetDao
import com.pennywiseai.tracker.data.database.dao.CategoryBudgetLimitDao
import com.pennywiseai.tracker.data.database.dao.TransactionSplitDao
import com.pennywiseai.tracker.data.database.entity.BudgetCategoryEntity
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetGroupRepository @Inject constructor(
    private val budgetDao: BudgetDao,
    private val transactionSplitDao: TransactionSplitDao,
    private val categoryBudgetLimitDao: CategoryBudgetLimitDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    fun getActiveGroups(): Flow<List<BudgetWithCategories>> =
        budgetDao.getActiveBudgetsWithCategories()

    fun getAssignedCategories(): Flow<List<String>> =
        budgetDao.getAllAssignedCategoryNames()

    suspend fun hasAnyGroups(): Boolean =
        budgetDao.getActiveGroupCount() > 0

    suspend fun createGroup(
        name: String,
        groupType: BudgetGroupType,
        color: String,
        currency: String,
        categories: List<Pair<String, BigDecimal>> = emptyList(),
        displayOrder: Int = 0
    ): Long {
        val totalAmount = categories.fold(BigDecimal.ZERO) { acc, (_, amount) -> acc + amount }
        val now = LocalDate.now()
        val yearMonth = YearMonth.from(now)

        val budget = BudgetEntity(
            name = name,
            limitAmount = totalAmount,
            periodType = BudgetPeriodType.MONTHLY,
            startDate = yearMonth.atDay(1),
            endDate = yearMonth.atEndOfMonth(),
            currency = currency,
            isActive = true,
            includeAllCategories = false,
            color = color,
            groupType = groupType,
            displayOrder = displayOrder,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val budgetId = budgetDao.insertBudget(budget)

        if (categories.isNotEmpty()) {
            val categoryEntities = categories.map { (categoryName, amount) ->
                BudgetCategoryEntity(
                    budgetId = budgetId,
                    categoryName = categoryName,
                    budgetAmount = amount
                )
            }
            budgetDao.insertBudgetCategories(categoryEntities)
        }

        return budgetId
    }

    suspend fun updateGroup(
        budgetId: Long,
        name: String,
        groupType: BudgetGroupType,
        color: String,
        categories: List<Pair<String, BigDecimal>>,
        currency: String? = null
    ) {
        val existing = budgetDao.getBudgetById(budgetId) ?: return
        val totalAmount = categories.fold(BigDecimal.ZERO) { acc, (_, amount) -> acc + amount }

        budgetDao.updateBudget(
            existing.copy(
                name = name,
                groupType = groupType,
                color = color,
                currency = currency ?: existing.currency,
                limitAmount = totalAmount,
                updatedAt = LocalDateTime.now()
            )
        )

        budgetDao.deleteCategoriesForBudget(budgetId)
        if (categories.isNotEmpty()) {
            val categoryEntities = categories.map { (categoryName, amount) ->
                BudgetCategoryEntity(
                    budgetId = budgetId,
                    categoryName = categoryName,
                    budgetAmount = amount
                )
            }
            budgetDao.insertBudgetCategories(categoryEntities)
        }
    }

    suspend fun deleteGroup(budgetId: Long) {
        budgetDao.deleteBudgetById(budgetId)
    }

    suspend fun addCategoryToGroup(budgetId: Long, categoryName: String, amount: BigDecimal) {
        budgetDao.insertBudgetCategory(
            BudgetCategoryEntity(
                budgetId = budgetId,
                categoryName = categoryName,
                budgetAmount = amount
            )
        )
        recomputeGroupTotal(budgetId)
    }

    suspend fun updateCategoryBudget(budgetId: Long, categoryName: String, amount: BigDecimal) {
        budgetDao.updateCategoryBudgetAmount(budgetId, categoryName, amount)
        recomputeGroupTotal(budgetId)
    }

    suspend fun removeCategoryFromGroup(budgetId: Long, categoryName: String) {
        budgetDao.deleteCategoryFromBudget(budgetId, categoryName)
        recomputeGroupTotal(budgetId)
    }

    private suspend fun recomputeGroupTotal(budgetId: Long) {
        val categories = budgetDao.getCategoriesForBudgetList(budgetId)
        val total = categories.sumOf { it.budgetAmount }
        budgetDao.updateBudgetLimitAmount(budgetId, total)
    }

    fun getGroupSpending(year: Int, month: Int, currency: String): Flow<BudgetOverallSummary> {
        val yearMonth = YearMonth.of(year, month)
        val startDate = yearMonth.atDay(1).atStartOfDay()
        val endDate = yearMonth.atEndOfMonth().atTime(23, 59, 59)

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
            budgetDao.getActiveBudgetsWithCategories(),
            transactionSplitDao.getTransactionsWithSplitsFiltered(startDate, endDate, currency)
        ) { groups, allTransactions ->
            buildSummary(groups, allTransactions, daysElapsed, daysRemaining, currency)
        }
    }

    fun getGroupSpendingAllCurrencies(year: Int, month: Int): Flow<BudgetGroupSpendingRaw> {
        val yearMonth = YearMonth.of(year, month)
        val startDate = yearMonth.atDay(1).atStartOfDay()
        val endDate = yearMonth.atEndOfMonth().atTime(23, 59, 59)

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

        val prevMonth = yearMonth.minusMonths(1)
        val prevStartDate = prevMonth.atDay(1).atStartOfDay()
        val prevEndDate = prevMonth.atEndOfMonth().atTime(23, 59, 59)

        return combine(
            budgetDao.getActiveBudgetsWithCategories(),
            transactionSplitDao.getTransactionsWithSplitsAllCurrencies(startDate, endDate),
            transactionSplitDao.getTransactionsWithSplitsAllCurrencies(prevStartDate, prevEndDate)
        ) { groups, allTransactions, prevTransactions ->
            BudgetGroupSpendingRaw(
                budgetsWithCategories = groups,
                allTransactions = allTransactions,
                prevTransactions = prevTransactions,
                daysElapsed = daysElapsed,
                daysRemaining = daysRemaining
            )
        }
    }

    fun buildSummary(
        groups: List<BudgetWithCategories>,
        allTransactions: List<com.pennywiseai.tracker.data.database.entity.TransactionWithSplits>,
        daysElapsed: Int,
        daysRemaining: Int,
        currency: String
    ): BudgetOverallSummary {
        val incomeTransactions = allTransactions.filter {
            it.transaction.transactionType == TransactionType.INCOME
        }
        val totalIncome = incomeTransactions.fold(BigDecimal.ZERO) { acc, tx ->
            acc + tx.transaction.amount
        }

        // Build category → amount map from all transactions (not just expenses)
        val categoryAmounts = mutableMapOf<String, BigDecimal>()
        allTransactions.forEach { txWithSplits ->
            if (txWithSplits.transaction.transactionType != TransactionType.INCOME) {
                txWithSplits.getAmountByCategory().forEach { (category, amount) ->
                    val categoryName = category.ifEmpty { "Others" }
                    categoryAmounts[categoryName] = (categoryAmounts[categoryName] ?: BigDecimal.ZERO) + amount
                }
            }
        }

        // Calculate total expenses for groups with no categories (track all expenses)
        val totalAllExpenses = categoryAmounts.values.fold(BigDecimal.ZERO) { acc, amount -> acc + amount }

        val groupSpendingList = groups.map { group ->
            val isTrackingAll = group.categories.isEmpty()

            if (isTrackingAll) {
                // No categories selected - track ALL expenses
                val totalBudget = group.budget.limitAmount
                val totalActual = totalAllExpenses
                val remaining = totalBudget - totalActual
                val pctUsed = if (totalBudget > BigDecimal.ZERO) {
                    (totalActual.toFloat() / totalBudget.toFloat() * 100f).coerceAtLeast(0f)
                } else 0f
                val dailyAllowance = if (daysRemaining > 0 && remaining > BigDecimal.ZERO) {
                    remaining.divide(BigDecimal(daysRemaining), 0, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO

                BudgetGroupSpending(
                    group = group,
                    categorySpending = emptyList(),
                    totalBudget = totalBudget,
                    totalActual = totalActual,
                    remaining = remaining,
                    percentageUsed = pctUsed,
                    dailyAllowance = dailyAllowance,
                    daysRemaining = daysRemaining,
                    daysElapsed = daysElapsed,
                    isTrackingAllExpenses = true
                )
            } else {
                // Normal case: track specific categories
                val catSpending = group.categories.map { cat ->
                    val actual = categoryAmounts[cat.categoryName] ?: BigDecimal.ZERO
                    val pctUsed = if (cat.budgetAmount > BigDecimal.ZERO) {
                        (actual.toFloat() / cat.budgetAmount.toFloat() * 100f).coerceAtLeast(0f)
                    } else 0f
                    val dailySpend = if (daysElapsed > 0 && actual > BigDecimal.ZERO) {
                        actual.divide(BigDecimal(daysElapsed), 0, RoundingMode.HALF_UP)
                    } else BigDecimal.ZERO
                    BudgetCategorySpending(
                        categoryName = cat.categoryName,
                        budgetAmount = cat.budgetAmount,
                        actualAmount = actual,
                        percentageUsed = pctUsed,
                        dailySpend = dailySpend
                    )
                }
                val totalBudget = group.totalBudgetAmount
                val totalActual = catSpending.fold(BigDecimal.ZERO) { acc, c -> acc + c.actualAmount }
                val remaining = totalBudget - totalActual
                val pctUsed = if (totalBudget > BigDecimal.ZERO) {
                    (totalActual.toFloat() / totalBudget.toFloat() * 100f).coerceAtLeast(0f)
                } else 0f
                val dailyAllowance = if (daysRemaining > 0 && remaining > BigDecimal.ZERO) {
                    remaining.divide(BigDecimal(daysRemaining), 0, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO

                BudgetGroupSpending(
                    group = group,
                    categorySpending = catSpending,
                    totalBudget = totalBudget,
                    totalActual = totalActual,
                    remaining = remaining,
                    percentageUsed = pctUsed,
                    dailyAllowance = dailyAllowance,
                    daysRemaining = daysRemaining,
                    daysElapsed = daysElapsed,
                    isTrackingAllExpenses = false
                )
            }
        }

        val limitGroups = groupSpendingList.filter { it.group.budget.groupType == BudgetGroupType.LIMIT }
        val targetGroups = groupSpendingList.filter { it.group.budget.groupType == BudgetGroupType.TARGET }
        val expectedGroups = groupSpendingList.filter { it.group.budget.groupType == BudgetGroupType.EXPECTED }

        val totalLimitBudget = limitGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalBudget }
        val totalLimitSpent = limitGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalActual }
        val totalTargetGoal = targetGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalBudget }
        val totalTargetActual = targetGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalActual }
        val totalExpectedBudget = expectedGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalBudget }
        val totalExpectedActual = expectedGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalActual }

        val netSavings = totalIncome - totalLimitSpent
        val savingsRate = if (totalIncome > BigDecimal.ZERO) {
            (netSavings.toFloat() / totalIncome.toFloat() * 100f)
        } else 0f

        val limitRemaining = totalLimitBudget - totalLimitSpent
        val dailyAllowance = if (daysRemaining > 0 && limitRemaining > BigDecimal.ZERO) {
            limitRemaining.divide(BigDecimal(daysRemaining), 0, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        return BudgetOverallSummary(
            groups = groupSpendingList,
            totalIncome = totalIncome,
            totalLimitBudget = totalLimitBudget,
            totalLimitSpent = totalLimitSpent,
            totalTargetGoal = totalTargetGoal,
            totalTargetActual = totalTargetActual,
            totalExpectedBudget = totalExpectedBudget,
            totalExpectedActual = totalExpectedActual,
            netSavings = netSavings,
            savingsRate = savingsRate,
            dailyAllowance = dailyAllowance,
            daysRemaining = daysRemaining,
            currency = currency
        )
    }

    suspend fun createSmartDefaults(baseCurrency: String) {
        // Group 1: Spending (LIMIT)
        createGroup(
            name = "Spending",
            groupType = BudgetGroupType.LIMIT,
            color = "#1565C0",
            currency = baseCurrency,
            categories = listOf(
                "Food & Dining" to BigDecimal.ZERO,
                "Groceries" to BigDecimal.ZERO,
                "Shopping" to BigDecimal.ZERO,
                "Entertainment" to BigDecimal.ZERO,
                "Personal Care" to BigDecimal.ZERO,
                "Transportation" to BigDecimal.ZERO,
                "Travel" to BigDecimal.ZERO,
                "Others" to BigDecimal.ZERO
            ),
            displayOrder = 0
        )

        // Group 2: Fixed Costs (EXPECTED)
        createGroup(
            name = "Fixed Costs",
            groupType = BudgetGroupType.EXPECTED,
            color = "#4CAF50",
            currency = baseCurrency,
            categories = listOf(
                "Bills & Utilities" to BigDecimal.ZERO,
                "Mobile" to BigDecimal.ZERO,
                "Insurance" to BigDecimal.ZERO,
                "Education" to BigDecimal.ZERO
            ),
            displayOrder = 1
        )

        // Group 3: Investments (TARGET)
        createGroup(
            name = "Investments",
            groupType = BudgetGroupType.TARGET,
            color = "#00D09C",
            currency = baseCurrency,
            categories = listOf(
                "Investments" to BigDecimal.ZERO,
                "Banking" to BigDecimal.ZERO
            ),
            displayOrder = 2
        )
    }

    suspend fun migrateFromOldBudget(
        oldLimit: BigDecimal?,
        oldCategoryLimits: List<com.pennywiseai.tracker.data.database.entity.CategoryBudgetLimitEntity>,
        currency: String
    ) {
        if (oldLimit == null && oldCategoryLimits.isEmpty()) return

        val categories = oldCategoryLimits.map { it.categoryName to it.limitAmount }
        createGroup(
            name = "Spending",
            groupType = BudgetGroupType.LIMIT,
            color = "#1565C0",
            currency = currency,
            categories = categories,
            displayOrder = 0
        )
    }

    suspend fun moveGroupUp(budgetId: Long) {
        val groups = budgetDao.getActiveBudgetsWithCategories().first()
            .sortedBy { it.budget.displayOrder }
        val index = groups.indexOfFirst { it.budget.id == budgetId }
        if (index > 0) {
            val current = groups[index].budget
            val above = groups[index - 1].budget
            budgetDao.updateBudget(current.copy(displayOrder = above.displayOrder, updatedAt = LocalDateTime.now()))
            budgetDao.updateBudget(above.copy(displayOrder = current.displayOrder, updatedAt = LocalDateTime.now()))
        }
    }

    suspend fun moveGroupDown(budgetId: Long) {
        val groups = budgetDao.getActiveBudgetsWithCategories().first()
            .sortedBy { it.budget.displayOrder }
        val index = groups.indexOfFirst { it.budget.id == budgetId }
        if (index >= 0 && index < groups.size - 1) {
            val current = groups[index].budget
            val below = groups[index + 1].budget
            budgetDao.updateBudget(current.copy(displayOrder = below.displayOrder, updatedAt = LocalDateTime.now()))
            budgetDao.updateBudget(below.copy(displayOrder = current.displayOrder, updatedAt = LocalDateTime.now()))
        }
    }
}
