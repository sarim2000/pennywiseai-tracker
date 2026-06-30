package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.BudgetDao
import com.pennywiseai.tracker.data.database.dao.BudgetSnapshotDao
import com.pennywiseai.tracker.data.database.dao.TransactionSplitDao
import com.pennywiseai.tracker.data.database.entity.BudgetCategoryEntity
import com.pennywiseai.tracker.data.database.entity.BudgetCategoryMonthSnapshotEntity
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetMonthSnapshotEntity
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.domain.model.BudgetCycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.flow
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
    private val snapshotDao: BudgetSnapshotDao,
    private val transactionSplitDao: TransactionSplitDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    /**
     * Resolves the actual [start, end] window for the budget cycle that begins
     * in the calendar month `(year, month)`, given the user's configurable
     * [startDay] (1..31). The end is the day before the next cycle's start,
     * which keeps consecutive cycles non-overlapping and gap-free even when
     * [startDay] exceeds the length of an intermediate month (e.g. Feb 30/31).
     */
    private fun cycleWindow(year: Int, month: Int, startDay: Int): Pair<LocalDate, LocalDate> {
        val cycleStart = YearMonth.of(year, month).atDay(1).let { firstOfMonth ->
            val safe = startDay.coerceIn(1, 31)
            val max = firstOfMonth.lengthOfMonth()
            firstOfMonth.withDayOfMonth(safe.coerceAtMost(max))
        }
        val cycleEnd = BudgetCycle.nextCycleStart(cycleStart, startDay).minusDays(1)
        return cycleStart to cycleEnd
    }


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
        buckets: List<BudgetBucketInput> = emptyList(),
        displayOrder: Int = -1,
        limitAmount: BigDecimal? = null,
        // Per-budget start date. When non-null, the budget's [startDate, endDate]
        // window is anchored to this date and the global cycle preference is
        // ignored. When null, falls back to today's calendar month (legacy
        // behaviour).
        customStartDate: LocalDate? = null,
        periodType: BudgetPeriodType = BudgetPeriodType.MONTHLY
    ): Long {
        val resolvedDisplayOrder = if (displayOrder < 0) budgetDao.getMaxDisplayOrder() + 1 else displayOrder
        val totalAmount = limitAmount ?: buckets.fold(BigDecimal.ZERO) { acc, b -> acc + b.amount }
        val now = LocalDate.now()
        val yearMonth = YearMonth.from(now)
        val startDay = runBlocking { userPreferencesRepository.getBudgetCycleStartDay() }
        val (startDate, endDate) = BudgetRepository.calculatePeriodDates(
            periodType = periodType,
            customStartDate = customStartDate,
            startDay = startDay
        )

        val budget = BudgetEntity(
            name = name,
            limitAmount = totalAmount,
            periodType = periodType,
            startDate = startDate,
            endDate = endDate,
            currency = currency,
            isActive = true,
            includeAllCategories = buckets.isEmpty(),
            color = color,
            groupType = groupType,
            displayOrder = resolvedDisplayOrder,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val budgetId = budgetDao.insertBudget(budget)

        if (buckets.isNotEmpty()) {
            val categoryEntities = buckets.map { b ->
                BudgetCategoryEntity(
                    budgetId = budgetId,
                    categoryName = b.name,
                    budgetAmount = b.amount,
                    matchType = b.matchType
                )
            }
            budgetDao.insertBudgetCategories(categoryEntities)
        }

        saveMonthSnapshot()
        return budgetId
    }

    suspend fun updateGroup(
        budgetId: Long,
        name: String,
        groupType: BudgetGroupType,
        color: String,
        buckets: List<BudgetBucketInput>,
        currency: String? = null,
        limitAmount: BigDecimal? = null,
        // Per-budget start date. When non-null, the [startDate, endDate] window
        // is recomputed from this date and the budget's existing periodType.
        // When null, the existing dates are left untouched.
        customStartDate: LocalDate? = null,
        periodType: BudgetPeriodType? = null
    ) {
        val existing = budgetDao.getBudgetById(budgetId) ?: return
        val totalAmount = limitAmount ?: buckets.fold(BigDecimal.ZERO) { acc, b -> acc + b.amount }
        val effectivePeriod = periodType ?: existing.periodType
        val (newStart, newEnd) = if (customStartDate != null) {
            val startDay = runBlocking { userPreferencesRepository.getBudgetCycleStartDay() }
            BudgetRepository.calculatePeriodDates(
                periodType = effectivePeriod,
                customStartDate = customStartDate,
                startDay = startDay
            )
        } else {
            existing.startDate to existing.endDate
        }

        budgetDao.updateBudget(
            existing.copy(
                name = name,
                groupType = groupType,
                color = color,
                currency = currency ?: existing.currency,
                limitAmount = totalAmount,
                periodType = effectivePeriod,
                startDate = newStart,
                endDate = newEnd,
                includeAllCategories = buckets.isEmpty(),
                updatedAt = LocalDateTime.now()
            )
        )

        budgetDao.deleteCategoriesForBudget(budgetId)
        if (buckets.isNotEmpty()) {
            val categoryEntities = buckets.map { b ->
                BudgetCategoryEntity(
                    budgetId = budgetId,
                    categoryName = b.name,
                    budgetAmount = b.amount,
                    matchType = b.matchType
                )
            }
            budgetDao.insertBudgetCategories(categoryEntities)
        }
        saveMonthSnapshot()
    }

    suspend fun deleteGroup(budgetId: Long) {
        budgetDao.deleteBudgetById(budgetId)
        saveMonthSnapshot()
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
        saveMonthSnapshot()
    }

    suspend fun updateCategoryBudget(budgetId: Long, categoryName: String, amount: BigDecimal) {
        budgetDao.updateCategoryBudgetAmount(budgetId, categoryName, amount)
        recomputeGroupTotal(budgetId)
        saveMonthSnapshot()
    }

    suspend fun removeCategoryFromGroup(budgetId: Long, categoryName: String) {
        budgetDao.deleteCategoryFromBudget(budgetId, categoryName)
        recomputeGroupTotal(budgetId)
        saveMonthSnapshot()
    }

    private suspend fun recomputeGroupTotal(budgetId: Long) {
        val categories = budgetDao.getCategoriesForBudgetList(budgetId)
        val total = categories.fold(BigDecimal.ZERO) { acc, cat -> acc + cat.budgetAmount }
        budgetDao.updateBudgetLimitAmount(budgetId, total)
    }

    private suspend fun saveMonthSnapshot() {
        val now = LocalDate.now()
        val year = now.year
        val month = now.monthValue
        val budgets = budgetDao.getActiveBudgetsWithCategories().first()
        val groupSnapshots = budgets.map { bwc ->
            BudgetMonthSnapshotEntity(
                budgetId = bwc.budget.id,
                year = year,
                month = month,
                budgetName = bwc.budget.name,
                limitAmount = bwc.budget.limitAmount,
                includeAllCategories = bwc.budget.includeAllCategories,
                color = bwc.budget.color,
                groupType = bwc.budget.groupType,
                displayOrder = bwc.budget.displayOrder
            )
        }
        val categorySnapshots = budgets.map { bwc ->
            bwc.categories.map { cat ->
                BudgetCategoryMonthSnapshotEntity(
                    budgetId = bwc.budget.id,
                    year = year,
                    month = month,
                    categoryName = cat.categoryName,
                    budgetAmount = cat.budgetAmount,
                    matchType = cat.matchType
                )
            }
        }.flatten()
        snapshotDao.replaceMonthSnapshots(year, month, groupSnapshots, categorySnapshots)
    }

    private suspend fun getGroupsForMonth(year: Int, month: Int): List<BudgetWithCategories> {
        val groupSnapshots = snapshotDao.getGroupSnapshots(year, month)
        if (groupSnapshots.isEmpty()) return emptyList()
        val catSnapshots = snapshotDao.getCategorySnapshots(year, month)
        return reconstructFromSnapshots(groupSnapshots, catSnapshots)
    }

    private fun reconstructFromSnapshots(
        groupSnapshots: List<BudgetMonthSnapshotEntity>,
        categorySnapshots: List<BudgetCategoryMonthSnapshotEntity>
    ): List<BudgetWithCategories> {
        return groupSnapshots.map { gs ->
            val cats = categorySnapshots
                .filter { it.budgetId == gs.budgetId }
                .map { cs ->
                    BudgetCategoryEntity(
                        budgetId = gs.budgetId,
                        categoryName = cs.categoryName,
                        budgetAmount = cs.budgetAmount,
                        matchType = cs.matchType
                    )
                }
            BudgetWithCategories(
                budget = BudgetEntity(
                    id = gs.budgetId,
                    name = gs.budgetName,
                    limitAmount = gs.limitAmount,
                    periodType = BudgetPeriodType.MONTHLY,
                    startDate = LocalDate.of(gs.year, gs.month, 1),
                    endDate = YearMonth.of(gs.year, gs.month).atEndOfMonth(),
                    isActive = true,
                    includeAllCategories = gs.includeAllCategories,
                    color = gs.color,
                    groupType = gs.groupType,
                    displayOrder = gs.displayOrder
                ),
                categories = cats
            )
        }
    }

    private fun percentOf(part: BigDecimal, total: BigDecimal, coerceMinZero: Boolean = true): Float {
        val pct = part.toFloat() / total.toFloat() * 100f
        return if (coerceMinZero) pct.coerceAtLeast(0f) else pct
    }

    fun getGroupSpending(year: Int, month: Int, currency: String): Flow<BudgetOverallSummary> {
        val today = LocalDate.now()
        val startDay = runBlocking { userPreferencesRepository.getBudgetCycleStartDay() }
        val (cycleStart, cycleEnd) = cycleWindow(year, month, startDay)
        val startDate = cycleStart.atStartOfDay()
        val endDate = cycleEnd.atTime(23, 59, 59)
        val isCurrentCycle = cycleStart <= today && today <= cycleEnd
        val daysElapsed = if (isCurrentCycle) {
            (ChronoUnit.DAYS.between(cycleStart, today).toInt() + 1).coerceAtLeast(1)
        } else {
            (ChronoUnit.DAYS.between(cycleStart, cycleEnd).toInt() + 1)
        }
        val daysRemaining = if (isCurrentCycle) {
            (ChronoUnit.DAYS.between(today, cycleEnd).toInt() + 1).coerceAtLeast(0)
        } else {
            0
        }
        val daysInMonth = (ChronoUnit.DAYS.between(cycleStart, cycleEnd).toInt() + 1)

        return if (isCurrentCycle) {
            combine(
                budgetDao.getActiveBudgetsWithCategories(),
                transactionSplitDao.getTransactionsWithSplitsFiltered(startDate, endDate, currency)
            ) { groups, allTransactions ->
                buildSummary(groups, allTransactions, daysElapsed, daysRemaining, currency, daysInMonth)
            }
        } else {
            flow {
                val groups = getGroupsForMonth(cycleEnd.year, cycleEnd.monthValue)
                transactionSplitDao.getTransactionsWithSplitsFiltered(startDate, endDate, currency)
                    .collect { allTransactions ->
                        emit(buildSummary(groups, allTransactions, daysElapsed, daysRemaining, currency, daysInMonth))
                    }
            }
        }
    }

    fun getGroupSpendingAllCurrencies(year: Int, month: Int): Flow<BudgetGroupSpendingRaw> {
        val today = LocalDate.now()
        val startDay = runBlocking { userPreferencesRepository.getBudgetCycleStartDay() }
        val (cycleStart, cycleEnd) = cycleWindow(year, month, startDay)
        val startDate = cycleStart.atStartOfDay()
        val endDate = cycleEnd.atTime(23, 59, 59)
        val isCurrentCycle = cycleStart <= today && today <= cycleEnd
        val daysElapsed = if (isCurrentCycle) {
            (ChronoUnit.DAYS.between(cycleStart, today).toInt() + 1).coerceAtLeast(1)
        } else {
            (ChronoUnit.DAYS.between(cycleStart, cycleEnd).toInt() + 1)
        }
        val daysRemaining = if (isCurrentCycle) {
            (ChronoUnit.DAYS.between(today, cycleEnd).toInt() + 1).coerceAtLeast(0)
        } else {
            0
        }
        val (prevStart, prevEnd) = BudgetCycle.previousCycle(cycleStart to cycleEnd, startDay)
        val prevStartDate = prevStart.atStartOfDay()
        val prevEndDate = prevEnd.atTime(23, 59, 59)

        return if (isCurrentCycle) {
            combine(
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
        } else {
            flow {
                val groups = getGroupsForMonth(cycleEnd.year, cycleEnd.monthValue)
                combine(
                    transactionSplitDao.getTransactionsWithSplitsAllCurrencies(startDate, endDate),
                    transactionSplitDao.getTransactionsWithSplitsAllCurrencies(prevStartDate, prevEndDate)
                ) { allTransactions, prevTransactions ->
                    BudgetGroupSpendingRaw(
                        budgetsWithCategories = groups,
                        allTransactions = allTransactions,
                        prevTransactions = prevTransactions,
                        daysElapsed = daysElapsed,
                        daysRemaining = daysRemaining
                    )
                }.collect { emit(it) }
            }
        }
    }

    suspend fun buildSummary(
        groups: List<BudgetWithCategories>,
        allTransactions: List<TransactionWithSplits>,
        daysElapsed: Int,
        daysRemaining: Int,
        currency: String,
        daysInMonth: Int = 30
    ): BudgetOverallSummary {
        // Exclude a Refund from totalIncome only when it's also being subtracted
        // from a category by aggregateBudgetCategorySpending (i.e. budgetCategory
        // is set). An orphaned DEDUCT_SPENT with no category isn't subtracted
        // from spend, so dropping it from income too would understate netSavings.
        val totalIncome = allTransactions.fold(BigDecimal.ZERO) { acc, txWithSplits ->
            val tx = txWithSplits.transaction
            if (tx.transactionType != TransactionType.INCOME) acc
            else if (tx.budgetImpactType == BudgetImpactType.DEDUCT_SPENT &&
                tx.budgetCategory != null
            ) acc
            else acc + tx.amount
        }

        // Single-currency: amounts are already in the requested currency, so the
        // converter lambdas are identities. The unified-currency caller in
        // BudgetGroupsViewModel injects real conversion via the same helper.
        val (categoryAmounts, categoryLimitBoosts, typeAmounts) = aggregateBudgetCategorySpending(
            transactions = allTransactions,
            convertSplit = { _, amount -> amount },
            convertIncome = { tx -> tx.amount }
        )

        // Calculate total expenses for groups with no categories (track all expenses)
        val totalAllExpenses = categoryAmounts.values.fold(BigDecimal.ZERO) { acc, amount -> acc + amount }

        // Helper: build per-group daily cumulative spending from transactions matching category names
        fun buildGroupPace(
            categoryNames: Set<String>?,  // null = all categories
            matchTypes: Set<String>,      // transaction-type buckets in this group
            groupBudget: BigDecimal
        ): Pair<List<Double>, List<Double>> {
            val effectiveDays = daysElapsed.coerceAtMost(daysInMonth)
            if (effectiveDays < 1) return emptyList<Double>() to emptyList()

            val dailyAmounts = DoubleArray(daysInMonth)
            allTransactions.forEach { txWithSplits ->
                val tx = txWithSplits.transaction
                if (tx.transactionType == TransactionType.INCOME ||
                    tx.transactionType == TransactionType.TRANSFER ||
                    tx.loanId != null
                ) return@forEach
                val day = tx.dateTime.dayOfMonth.coerceIn(1, daysInMonth)
                if (tx.transactionType.name in matchTypes) {
                    // Claimed by a type bucket in this group (e.g. INVESTMENT).
                    dailyAmounts[day - 1] += tx.amount.toDouble()
                } else if (tx.transactionType !in BUDGET_TYPE_BUCKETS) {
                    // Category routing for expenses/credit. Type-bucket-eligible
                    // transactions (investments) never fall through to categories.
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
            // Subtract Refund (DEDUCT_SPENT) income on the day it occurred so the
            // cumulative endpoint tracks the same "actual" figure shown on the row.
            allTransactions.forEach { txWithSplits ->
                val tx = txWithSplits.transaction
                if (tx.transactionType != TransactionType.INCOME) return@forEach
                if (tx.budgetImpactType != BudgetImpactType.DEDUCT_SPENT) return@forEach
                val category = tx.budgetCategory ?: return@forEach
                if (categoryNames != null && category !in categoryNames) return@forEach
                val day = tx.dateTime.dayOfMonth.coerceIn(1, daysInMonth)
                dailyAmounts[day - 1] -= tx.amount.toDouble()
            }
            // Carry the running total forward unclamped so a refund dated before
            // the first expense still nets out against later expenses; only the
            // emitted value is clamped, so the chart endpoint matches the
            // per-category floor used in aggregateBudgetCategorySpending.
            val cumulative = mutableListOf<Double>()
            var runningNet = 0.0
            for (i in 0 until effectiveDays) {
                runningNet += dailyAmounts[i]
                cumulative.add(runningNet.coerceAtLeast(0.0))
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
                    percentOf(totalActual, totalBudget)
                } else 0f
                val dailyAllowance = if (daysRemaining > 0 && remaining > BigDecimal.ZERO) {
                    remaining.divide(BigDecimal(daysRemaining), 0, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO
                val (cumSpending, budgetPace) = buildGroupPace(null, emptySet(), totalBudget)

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
                    // Type buckets read per-type spend; category buckets read
                    // per-category spend (with any Extra-budget income boost).
                    val actual = if (cat.matchType != null) {
                        typeAmounts[cat.matchType] ?: BigDecimal.ZERO
                    } else {
                        categoryAmounts[cat.categoryName] ?: BigDecimal.ZERO
                    }
                    val boost = if (cat.matchType != null) BigDecimal.ZERO
                        else categoryLimitBoosts[cat.categoryName] ?: BigDecimal.ZERO
                    val effectiveBudget = cat.budgetAmount + boost
                    val pctUsed = if (effectiveBudget > BigDecimal.ZERO) {
                        percentOf(actual, effectiveBudget)
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
                // "Category Limits" are optional — the group-level limit is the
                // source of truth when set, with the per-cat sum as a fallback
                // for budgets that only define per-cat amounts.
                val totalBudget = if (group.budget.limitAmount > BigDecimal.ZERO) {
                    group.budget.limitAmount
                } else {
                    catSpending.fold(BigDecimal.ZERO) { acc, c -> acc + c.budgetAmount }
                }
                val totalActual = catSpending.fold(BigDecimal.ZERO) { acc, c -> acc + c.actualAmount }
                val remaining = totalBudget - totalActual
                val pctUsed = if (totalBudget > BigDecimal.ZERO) {
                    percentOf(totalActual, totalBudget)
                } else 0f
                val dailyAllowance = if (daysRemaining > 0 && remaining > BigDecimal.ZERO) {
                    remaining.divide(BigDecimal(daysRemaining), 0, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO
                val catNames = group.categories.filter { it.matchType == null }
                    .map { it.categoryName }.toSet()
                val matchTypes = group.categories.mapNotNull { it.matchType }.toSet()
                val (cumSpending, budgetPace) = buildGroupPace(catNames, matchTypes, totalBudget)

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
            percentOf(netSavings, totalIncome, coerceMinZero = false)
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
            buckets = listOf(
                BudgetBucketInput("Food & Dining", amount(5000)),
                BudgetBucketInput("Groceries", amount(8000)),
                BudgetBucketInput("Shopping", amount(3000)),
                BudgetBucketInput("Entertainment", amount(2000)),
                BudgetBucketInput("Personal Care", amount(1000)),
                BudgetBucketInput("Transportation", amount(3000)),
                BudgetBucketInput("Travel", amount(5000)),
                BudgetBucketInput("Others", amount(3000))
            ),
            displayOrder = 0
        )

        // Group 2: Fixed Costs (EXPECTED) - recurring bills
        createGroup(
            name = "Fixed Costs",
            groupType = BudgetGroupType.EXPECTED,
            color = "#4CAF50",
            currency = baseCurrency,
            buckets = listOf(
                BudgetBucketInput("Bills & Utilities", amount(5000)),
                BudgetBucketInput("Mobile", amount(500)),
                BudgetBucketInput("Insurance", amount(2000)),
                BudgetBucketInput("Education", amount(3000))
            ),
            displayOrder = 1
        )

        // Group 3: Investments (TARGET) — tracks the INVESTMENT transaction type
        // directly, so every investment counts regardless of its category and
        // nothing leaks into Spending.
        createGroup(
            name = "Investments",
            groupType = BudgetGroupType.TARGET,
            color = "#00D09C",
            currency = baseCurrency,
            buckets = listOf(
                BudgetBucketInput(
                    name = "Investments",
                    amount = amount(15000),
                    matchType = TransactionType.INVESTMENT.name
                )
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

    companion object {
        /**
         * Per-category spend after applying any INCOME-side budget impacts,
         * plus the per-category budget bumps contributed by Extra-budget income.
         */
        data class CategoryAggregation(
            val categoryAmounts: Map<String, BigDecimal>,
            val categoryLimitBoosts: Map<String, BigDecimal>,
            // Spend per transaction TYPE (key = TransactionType.name, e.g.
            // "INVESTMENT"), for budget buckets that track a type instead of a
            // category. INVESTMENT outflows route here, never into categoryAmounts,
            // so they only count toward a type bucket and never leak into a
            // category/Spending budget. Third field → existing 2-arg destructuring
            // still compiles.
            val typeAmounts: Map<String, BigDecimal> = emptyMap()
        )

        /** Transaction types that can back a budget "type bucket". */
        val BUDGET_TYPE_BUCKETS = setOf(TransactionType.INVESTMENT)

        /**
         * Single source of truth for the per-category aggregation used on the
         * Budgets screen. Walks `transactions` and:
         *  - sums non-INCOME / non-TRANSFER split amounts into `categoryAmounts`
         *    (key=category name, "Others" when blank), EXCEPT type-bucket-eligible
         *    types (INVESTMENT) which go to `typeAmounts` keyed by type name;
         *  - for INCOME txns with a `budgetCategory`, subtracts DEDUCT_SPENT
         *    (Refund) amounts from `categoryAmounts` (floored at zero) and
         *    accumulates ADD_TO_LIMIT (Extra budget) amounts into
         *    `categoryLimitBoosts`.
         *
         * `convertSplit` and `convertIncome` let callers project amounts into a
         * display currency. Same-currency callers pass identity lambdas; the
         * unified-currency call site supplies `CurrencyConversionService`-backed
         * suspending converters.
         */
        suspend fun aggregateBudgetCategorySpending(
            transactions: List<TransactionWithSplits>,
            convertSplit: suspend (fromCurrency: String, amount: BigDecimal) -> BigDecimal,
            convertIncome: suspend (TransactionEntity) -> BigDecimal
        ): CategoryAggregation {
            val categoryAmounts = mutableMapOf<String, BigDecimal>()
            val typeAmounts = mutableMapOf<String, BigDecimal>()
            for (txWithSplits in transactions) {
                val type = txWithSplits.transaction.transactionType
                if (type == TransactionType.INCOME || type == TransactionType.TRANSFER) continue
                val fromCurrency = txWithSplits.transaction.currency
                if (type in BUDGET_TYPE_BUCKETS) {
                    // Route the whole amount to its type bucket, ignoring category —
                    // so it counts only toward a type-tracking budget and never
                    // contributes to a category (Spending) budget.
                    val converted = convertSplit(fromCurrency, txWithSplits.transaction.amount)
                    typeAmounts[type.name] = (typeAmounts[type.name] ?: BigDecimal.ZERO) + converted
                    continue
                }
                for ((category, amount) in txWithSplits.getAmountByCategory()) {
                    val categoryName = category.ifEmpty { "Others" }
                    val converted = convertSplit(fromCurrency, amount)
                    categoryAmounts[categoryName] =
                        (categoryAmounts[categoryName] ?: BigDecimal.ZERO) + converted
                }
            }

            val categoryLimitBoosts = mutableMapOf<String, BigDecimal>()
            for (txWithSplits in transactions) {
                val tx = txWithSplits.transaction
                if (tx.transactionType != TransactionType.INCOME) continue
                val category = tx.budgetCategory ?: continue
                val impact = tx.budgetImpactType ?: continue
                val amount = convertIncome(tx)
                when (impact) {
                    BudgetImpactType.DEDUCT_SPENT -> {
                        val current = categoryAmounts[category] ?: BigDecimal.ZERO
                        categoryAmounts[category] = (current - amount).coerceAtLeast(BigDecimal.ZERO)
                    }
                    BudgetImpactType.ADD_TO_LIMIT -> {
                        categoryLimitBoosts[category] =
                            (categoryLimitBoosts[category] ?: BigDecimal.ZERO) + amount
                    }
                }
            }

            return CategoryAggregation(categoryAmounts, categoryLimitBoosts, typeAmounts)
        }
    }
}
