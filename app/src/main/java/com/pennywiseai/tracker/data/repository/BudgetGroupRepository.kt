package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.BudgetDao
import com.pennywiseai.tracker.data.database.dao.TransactionSplitDao
import com.pennywiseai.tracker.data.database.entity.BudgetCategoryEntity
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
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
        displayOrder: Int = -1,
        limitAmount: BigDecimal? = null
    ): Long {
        val resolvedDisplayOrder = if (displayOrder < 0) budgetDao.getMaxDisplayOrder() + 1 else displayOrder
        val totalAmount = limitAmount ?: categories.fold(BigDecimal.ZERO) { acc, (_, amount) -> acc + amount }
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
            includeAllCategories = categories.isEmpty(),
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
        currency: String? = null,
        limitAmount: BigDecimal? = null
    ) {
        val existing = budgetDao.getBudgetById(budgetId) ?: return
        val totalAmount = limitAmount ?: categories.fold(BigDecimal.ZERO) { acc, (_, amount) -> acc + amount }

        budgetDao.updateBudget(
            existing.copy(
                name = name,
                groupType = groupType,
                color = color,
                currency = currency ?: existing.currency,
                limitAmount = totalAmount,
                includeAllCategories = categories.isEmpty(),
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

        val daysInMonth = yearMonth.lengthOfMonth()

        return combine(
            budgetDao.getActiveBudgetsWithCategories(),
            transactionSplitDao.getTransactionsWithSplitsFiltered(startDate, endDate, currency)
        ) { groups, allTransactions ->
            buildSummary(groups, allTransactions, daysElapsed, daysRemaining, currency, daysInMonth)
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
        currency: String,
        daysInMonth: Int = 30
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
            if (txWithSplits.transaction.transactionType != TransactionType.INCOME &&
                txWithSplits.transaction.transactionType != TransactionType.TRANSFER &&
                txWithSplits.transaction.transactionType != TransactionType.INVESTMENT) {
                txWithSplits.getAmountByCategory().forEach { (category, amount) ->
                    val categoryName = category.ifEmpty { "Others" }
                    categoryAmounts[categoryName] = (categoryAmounts[categoryName] ?: BigDecimal.ZERO) + amount
                }
            }
        }

        // Apply income transactions that are linked to budget categories
        val categoryLimitBoosts = mutableMapOf<String, BigDecimal>()
        allTransactions.forEach { txWithSplits ->
            val tx = txWithSplits.transaction
            if (tx.transactionType == TransactionType.INCOME && tx.budgetCategory != null) {
                when (tx.budgetImpactType) {
                    BudgetImpactType.DEDUCT_SPENT -> {
                        val current = categoryAmounts[tx.budgetCategory] ?: BigDecimal.ZERO
                        categoryAmounts[tx.budgetCategory] = (current - tx.amount).coerceAtLeast(BigDecimal.ZERO)
                    }
                    BudgetImpactType.ADD_TO_LIMIT -> {
                        categoryLimitBoosts[tx.budgetCategory] = (categoryLimitBoosts[tx.budgetCategory] ?: BigDecimal.ZERO) + tx.amount
                    }
                    null -> {}
                }
            }
        }

        // Calculate total expenses for groups with no categories (track all expenses)
        val totalAllExpenses = categoryAmounts.values.fold(BigDecimal.ZERO) { acc, amount -> acc + amount }

        // Helper: build per-group daily cumulative spending from transactions matching category names
        fun buildGroupPace(
            categoryNames: Set<String>?,  // null = all categories
            groupBudget: BigDecimal
        ): Pair<List<Double>, List<Double>> {
            val effectiveDays = daysElapsed.coerceAtMost(daysInMonth)
            if (effectiveDays < 1) return emptyList<Double>() to emptyList()

            val dailyAmounts = DoubleArray(daysInMonth)
            allTransactions.forEach { txWithSplits ->
                val tx = txWithSplits.transaction
                if (tx.transactionType != TransactionType.INCOME &&
                    tx.transactionType != TransactionType.TRANSFER &&
                    tx.transactionType != TransactionType.INVESTMENT &&
                    tx.loanId == null
                ) {
                    val day = tx.dateTime.dayOfMonth.coerceIn(1, daysInMonth)
                    if (categoryNames == null) {
                        dailyAmounts[day - 1] += tx.amount.toDouble()
                    } else {
                        txWithSplits.getAmountByCategory().forEach { (cat, amount) ->
                            val catName = cat.ifEmpty { "Others" }
                            if (catName in categoryNames) {
                                dailyAmounts[day - 1] += amount.toDouble()
                            }
                        }
                    }
                }
            }
            val cumulative = mutableListOf<Double>()
            var running = 0.0
            for (i in 0 until effectiveDays) {
                running += dailyAmounts[i]
                cumulative.add(running)
            }
            val pace = if (groupBudget > BigDecimal.ZERO) {
                val dailyPace = groupBudget.toDouble() / daysInMonth
                (1..effectiveDays).map { it * dailyPace }
            } else emptyList()
            return cumulative to pace
        }

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
                val (cumSpending, budgetPace) = buildGroupPace(null, totalBudget)

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
                    isTrackingAllExpenses = true,
                    dailyCumulativeSpending = cumSpending,
                    dailyBudgetPace = budgetPace
                )
            } else {
                // Normal case: track specific categories
                val catSpending = group.categories.map { cat ->
                    val actual = categoryAmounts[cat.categoryName] ?: BigDecimal.ZERO
                    val effectiveBudget = cat.budgetAmount + (categoryLimitBoosts[cat.categoryName] ?: BigDecimal.ZERO)
                    val pctUsed = if (effectiveBudget > BigDecimal.ZERO) {
                        (actual.toFloat() / effectiveBudget.toFloat() * 100f).coerceAtLeast(0f)
                    } else 0f
                    val dailySpend = if (daysElapsed > 0 && actual > BigDecimal.ZERO) {
                        actual.divide(BigDecimal(daysElapsed), 0, RoundingMode.HALF_UP)
                    } else BigDecimal.ZERO
                    BudgetCategorySpending(
                        categoryName = cat.categoryName,
                        budgetAmount = effectiveBudget,
                        actualAmount = actual,
                        percentageUsed = pctUsed,
                        dailySpend = dailySpend
                    )
                }
                val totalBudget = catSpending.fold(BigDecimal.ZERO) { acc, c -> acc + c.budgetAmount }
                val totalActual = catSpending.fold(BigDecimal.ZERO) { acc, c -> acc + c.actualAmount }
                val remaining = totalBudget - totalActual
                val pctUsed = if (totalBudget > BigDecimal.ZERO) {
                    (totalActual.toFloat() / totalBudget.toFloat() * 100f).coerceAtLeast(0f)
                } else 0f
                val dailyAllowance = if (daysRemaining > 0 && remaining > BigDecimal.ZERO) {
                    remaining.divide(BigDecimal(daysRemaining), 0, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO
                val catNames = group.categories.map { it.categoryName }.toSet()
                val (cumSpending, budgetPace) = buildGroupPace(catNames, totalBudget)

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
                    isTrackingAllExpenses = false,
                    dailyCumulativeSpending = cumSpending,
                    dailyBudgetPace = budgetPace
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
        // Default amounts based on typical monthly spending
        // Users can customize these after creation
        val isINR = baseCurrency.uppercase() == "INR" || baseCurrency == "₹"

        // Multiplier: INR defaults vs USD/other defaults
        val m = if (isINR) BigDecimal.ONE else BigDecimal("0.012") // ~1 USD = 83 INR

        fun amount(inrAmount: Long): BigDecimal =
            BigDecimal(inrAmount).multiply(m).setScale(0, RoundingMode.HALF_UP)

        // Group 1: Spending (LIMIT) - discretionary spending you want to cap
        createGroup(
            name = "Spending",
            groupType = BudgetGroupType.LIMIT,
            color = "#1565C0",
            currency = baseCurrency,
            categories = listOf(
                "Food & Dining" to amount(5000),
                "Groceries" to amount(8000),
                "Shopping" to amount(3000),
                "Entertainment" to amount(2000),
                "Personal Care" to amount(1000),
                "Transportation" to amount(3000),
                "Travel" to amount(5000),
                "Others" to amount(3000)
            ),
            displayOrder = 0
        )

        // Group 2: Fixed Costs (EXPECTED) - recurring bills
        createGroup(
            name = "Fixed Costs",
            groupType = BudgetGroupType.EXPECTED,
            color = "#4CAF50",
            currency = baseCurrency,
            categories = listOf(
                "Bills & Utilities" to amount(5000),
                "Mobile" to amount(500),
                "Insurance" to amount(2000),
                "Education" to amount(3000)
            ),
            displayOrder = 1
        )

        // Group 3: Investments (TARGET) - savings goals
        createGroup(
            name = "Investments",
            groupType = BudgetGroupType.TARGET,
            color = "#00D09C",
            currency = baseCurrency,
            categories = listOf(
                "Investments" to amount(10000),
                "Banking" to amount(5000)
            ),
            displayOrder = 2
        )
    }

    suspend fun moveGroupUp(budgetId: Long) {
        val groups = budgetDao.getActiveBudgets().first()
            .sortedBy { it.displayOrder }
        val index = groups.indexOfFirst { it.id == budgetId }
        if (index > 0) {
            val current = groups[index]
            val above = groups[index - 1]
            budgetDao.updateBudget(current.copy(displayOrder = above.displayOrder, updatedAt = LocalDateTime.now()))
            budgetDao.updateBudget(above.copy(displayOrder = current.displayOrder, updatedAt = LocalDateTime.now()))
        }
    }

    suspend fun moveGroupDown(budgetId: Long) {
        val groups = budgetDao.getActiveBudgets().first()
            .sortedBy { it.displayOrder }
        val index = groups.indexOfFirst { it.id == budgetId }
        if (index >= 0 && index < groups.size - 1) {
            val current = groups[index]
            val below = groups[index + 1]
            budgetDao.updateBudget(current.copy(displayOrder = below.displayOrder, updatedAt = LocalDateTime.now()))
            budgetDao.updateBudget(below.copy(displayOrder = current.displayOrder, updatedAt = LocalDateTime.now()))
        }
    }
}
