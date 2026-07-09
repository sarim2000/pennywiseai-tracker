package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.BudgetDao
import com.pennywiseai.tracker.data.database.dao.TransactionSplitDao
import com.pennywiseai.tracker.data.database.entity.BudgetCategoryEntity
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
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
    /**
     * Resolves the actual [start, end] window for the budget cycle that begins
     * in the calendar month `(year, month)`, given the user's configurable
     * [startDay] (1..31). The end is the day before the next cycle's start,
     * which keeps consecutive cycles non-overlapping and gap-free even when
     * [startDay] exceeds the length of an intermediate month (e.g. Feb 30/31).
     */


    fun getActiveGroups(): Flow<List<BudgetWithCategories>> =
        budgetDao.getActiveBudgetsWithCategories()

    fun getAssignedCategories(): Flow<List<String>> =
        budgetDao.getAllAssignedCategoryNames()

    suspend fun hasAnyGroups(): Boolean =
        budgetDao.getActiveGroupCount() > 0

    /**
     * Single-budget lookup by id, used by the Budget History drill-down
     * screen. Returns null if the budget was deleted.
     */
    suspend fun getGroupById(budgetId: Long): BudgetEntity? =
        budgetDao.getBudgetById(budgetId)

    suspend fun createGroup(
        name: String,
        groupType: BudgetGroupType,
        color: String,
        currency: String,
        buckets: List<BudgetBucketInput> = emptyList(),
        displayOrder: Int = -1,
        limitAmount: BigDecimal? = null,
        periodType: BudgetPeriodType = BudgetPeriodType.MONTHLY,
        // Cadence anchors. WEEKLY uses [weekStartDay] (1=Mon..7=Sun);
        // MONTHLY uses [monthStartDay] (1..31); CUSTOM ignores both and
        // uses the literal [startDate, endDate] the user picked.
        weekStartDay: Int? = null,
        monthStartDay: Int? = null,
        // For CUSTOM: the literal start date the user picked.
        startDate: LocalDate? = null,
        endDate: LocalDate? = null
    ): Long {
        val resolvedDisplayOrder = if (displayOrder < 0) budgetDao.getMaxDisplayOrder() + 1 else displayOrder
        val totalAmount = limitAmount ?: buckets.fold(BigDecimal.ZERO) { acc, b -> acc + b.amount }
        val now = LocalDate.now()
        val startDay = userPreferencesRepository.getBudgetCycleStartDay()
        // Seed the [startDate, endDate] cache with the *current* window for
        // the budget's period type so the row is valid before the read-time
        // resolver re-runs. For CUSTOM the user-supplied literal dates win.
        val (resolvedStart, resolvedEnd) = when (periodType) {
            BudgetPeriodType.CUSTOM -> (startDate ?: now) to (endDate ?: now.plusMonths(1))
            BudgetPeriodType.WEEKLY -> {
                val dow = weekStartDay?.coerceIn(1, 7) ?: 1
                val s = now.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.of(dow)))
                s to s.plusDays(6)
            }
            BudgetPeriodType.MONTHLY -> {
                val day = monthStartDay?.let { BudgetCycle.clampStartDay(it) } ?: startDay
                BudgetCycle.currentCycle(now, day)
            }
        }

        val budget = BudgetEntity(
            name = name,
            limitAmount = totalAmount,
            periodType = periodType,
            startDate = resolvedStart,
            endDate = resolvedEnd,
            weekStartDay = if (periodType == BudgetPeriodType.WEEKLY) weekStartDay?.coerceIn(1, 7) else null,
            monthStartDay = if (periodType == BudgetPeriodType.MONTHLY) monthStartDay?.let { BudgetCycle.clampStartDay(it) } else null,
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
        periodType: BudgetPeriodType? = null,
        weekStartDay: Int? = null,
        monthStartDay: Int? = null,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null
    ) {
        val existing = budgetDao.getBudgetById(budgetId) ?: return
        val totalAmount = limitAmount ?: buckets.fold(BigDecimal.ZERO) { acc, b -> acc + b.amount }
        val effectivePeriod = periodType ?: existing.periodType
        val now = LocalDate.now()
        val globalStartDay = userPreferencesRepository.getBudgetCycleStartDay()

        // Resolve the new [startDate, endDate] cache. For CUSTOM the user
        // supplied literal dates; for WEEKLY/MONTHLY we recompute the
        // *current* window so the row is valid before the read-time resolver
        // re-runs. Anchor fields are also updated so the resolver picks the
        // right anchor next time.
        val (newStart, newEnd, newWeek, newMonth) = when (effectivePeriod) {
            BudgetPeriodType.CUSTOM -> {
                val s = startDate ?: existing.startDate
                val e = endDate ?: existing.endDate
                Quadruple(s, e, null as Int?, null as Int?)
            }
            BudgetPeriodType.WEEKLY -> {
                val dow = weekStartDay?.coerceIn(1, 7) ?: existing.weekStartDay ?: 1
                val s = now.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.of(dow)))
                Quadruple(s, s.plusDays(6), dow as Int?, null as Int?)
            }
            BudgetPeriodType.MONTHLY -> {
                val day = monthStartDay?.let { BudgetCycle.clampStartDay(it) }
                    ?: existing.monthStartDay
                    ?: BudgetCycle.clampStartDay(globalStartDay)
                val (s, e) = BudgetCycle.currentCycle(now, day)
                Quadruple(s, e, null as Int?, day as Int?)
            }
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
                weekStartDay = newWeek,
                monthStartDay = newMonth,
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

    }

    suspend fun deleteGroup(budgetId: Long) {
        budgetDao.deleteBudgetById(budgetId)

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
        val total = categories.fold(BigDecimal.ZERO) { acc, cat -> acc + cat.budgetAmount }
        budgetDao.updateBudgetLimitAmount(budgetId, total)
    }

    private fun percentOf(part: BigDecimal, total: BigDecimal, coerceMinZero: Boolean = true): Float {
        val pct = part.toFloat() / total.toFloat() * 100f
        return if (coerceMinZero) pct.coerceAtLeast(0f) else pct
    }

    /**
     * Per-window spend pipeline.
     *
     * For the selected (year, month), each active budget's
     * [windowsForMonth] produces a list of windows it covers in that
     * month (one per week for Weekly, one cycle for Monthly, the
     * literal range for One-time). We run one spend query per (budget,
     * window) and aggregate into a per-budget [BudgetGroupSpending].
     *
     * For the **current** month the displayed window is
     * [resolveBudgetWindow] (today-anchored, so it rolls naturally as
     * the week / month / one-time progresses). For a **historical** month
     * the displayed window is the one in the list that contains the most
     * recent day in the month, and the older windows go into
     * [BudgetGroupSpending.previousWindows] for the per-week sub-list.
     */
    fun getGroupSpending(year: Int, month: Int, currency: String): Flow<BudgetOverallSummary> {
        val today = LocalDate.now()
        val ym = YearMonth.of(year, month)
        val monthStart = ym.atDay(1)
        val monthEnd = ym.atEndOfMonth()
        val isCurrentMonth = year == today.year && month == today.monthValue

        return combine(
            budgetDao.getActiveBudgetsWithCategories(),
            userPreferencesRepository.budgetCycleStartDay
        ) { groups, startDay ->
            Pair(groups, startDay)
        }.flatMapLatest { (groups, startDay) ->
            val perBudgetWindows = groups.map { g ->
                g to windowsForMonth(g.budget, year, month, startDay)
            }
            
            val allWindows = perBudgetWindows.flatMap { (g, windows) ->
                val displayWindow = if (isCurrentMonth) {
                    resolveBudgetWindow(g.budget, today, startDay)
                } else {
                    windows.lastOrNull() ?: BudgetWindow(monthStart, monthStart, 0)
                }
                listOf(displayWindow) + windows.filter { it != displayWindow }
            }.distinct()

            val minStart = allWindows.minOfOrNull { it.start } ?: monthStart
            val maxEnd = allWindows.maxOfOrNull { it.end } ?: monthEnd
            val pageWindowDays = if (minStart != monthStart || maxEnd != monthEnd) {
                java.time.temporal.ChronoUnit.DAYS.between(minStart, maxEnd).toInt() + 1
            } else {
                monthEnd.dayOfMonth
            }
            val pageWindow = BudgetWindow(minStart, maxEnd, pageWindowDays)
            val queryEnd = maxEnd
            val queryStart = minStart

            transactionSplitDao.getTransactionsWithSplitsFiltered(
                queryStart.atStartOfDay(),
                queryEnd.atTime(23, 59, 59),
                currency
            ).map { allTxs ->
                val groupSpendings = perBudgetWindows.map { (group, windows) ->
                    val budget = group.budget
                    if (windows.isEmpty()) {
                        val empty = BudgetWindow(monthStart, monthStart, 0)
                        buildSummaryFromWindows(
                            group = group,
                            displayWindow = empty,
                            previousWindows = emptyList(),
                            transactionsByWindow = emptyMap(),
                            today = today,
                            isCurrentMonth = isCurrentMonth,
                            monthStart = monthStart,
                            monthEnd = monthEnd,
                            currency = currency
                        )
                    } else {
                        val displayWindow = if (isCurrentMonth) {
                            resolveBudgetWindow(budget, today, startDay)
                        } else {
                            windows.lastOrNull() ?: BudgetWindow(monthStart, monthStart, 0)
                        }
                        val otherWindows = windows.filter { it != displayWindow }
                        val transactionsByWindow = (listOf(displayWindow) + otherWindows)
                            .associateWith { w ->
                                val capDate = if (isCurrentWeekInCurrentMonth(w, today, isCurrentMonth)) {
                                    today
                                } else {
                                    monthEnd
                                }
                                val effectiveEnd = if (capDate.isBefore(w.end)) capDate else w.end
                                allTxs.filter { tx ->
                                    val d = tx.transaction.dateTime.toLocalDate()
                                    !d.isBefore(w.start) && !d.isAfter(effectiveEnd)
                                }
                            }
                        val previous = if (budget.periodType == BudgetPeriodType.WEEKLY) {
                            otherWindows.map { w ->
                                val isLive = isCurrentWeekInCurrentMonth(w, today, isCurrentMonth)
                                val cap = if (isLive) today else monthEnd
                                PastWindowSpending(
                                    window = w,
                                    spent = getBudgetWindowFilteredSpend(group, transactionsByWindow[w].orEmpty()),
                                    capDate = cap,
                                    isLive = isLive
                                )
                            }
                        } else emptyList()

                        buildSummaryFromWindows(
                            group = group,
                            displayWindow = displayWindow,
                            previousWindows = previous,
                            transactionsByWindow = transactionsByWindow,
                            today = today,
                            isCurrentMonth = isCurrentMonth,
                            monthStart = monthStart,
                            monthEnd = monthEnd,
                            currency = currency,
                            displayedCapDate = if (isCurrentWeekInCurrentMonth(displayWindow, today, isCurrentMonth)) today else monthEnd,
                            displayedIsLive = isCurrentWeekInCurrentMonth(displayWindow, today, isCurrentMonth)
                        )
                    }
                }

                val totalLimitBudget = groupSpendings.filter { it.group.budget.groupType == BudgetGroupType.LIMIT }
                    .fold(BigDecimal.ZERO) { acc, g -> acc + g.totalBudget }
                val totalLimitSpent = groupSpendings.filter { it.group.budget.groupType == BudgetGroupType.LIMIT }
                    .fold(BigDecimal.ZERO) { acc, g -> acc + g.totalActual }
                
                // Compute totalIncome from allTxs that fall within pageWindow
                val totalIncome = allTxs.filter { tx ->
                    val d = tx.transaction.dateTime.toLocalDate()
                    !d.isBefore(pageWindow.start) && !d.isAfter(pageWindow.end)
                }.fold(BigDecimal.ZERO) { acc, tx ->
                    val t = tx.transaction
                    if (t.transactionType == TransactionType.INCOME &&
                        !(t.budgetImpactType == BudgetImpactType.DEDUCT_SPENT && t.budgetCategory != null)
                    ) acc + t.amount else acc
                }

                val limitRemaining = totalLimitBudget - totalLimitSpent
                val daysRemaining = (ChronoUnit.DAYS.between(today, pageWindow.end).toInt() + 1)
                    .coerceIn(0, pageWindow.days)
                val dailyAllowance = if (daysRemaining > 0 && limitRemaining > BigDecimal.ZERO) {
                    limitRemaining.divide(BigDecimal(daysRemaining), 0, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO

                val totalTargetGoal = groupSpendings.filter { it.group.budget.groupType == BudgetGroupType.TARGET }
                    .fold(BigDecimal.ZERO) { acc, g -> acc + g.totalBudget }
                val totalTargetActual = groupSpendings.filter { it.group.budget.groupType == BudgetGroupType.TARGET }
                    .fold(BigDecimal.ZERO) { acc, g -> acc + g.totalActual }
                val totalExpectedBudget = groupSpendings.filter { it.group.budget.groupType == BudgetGroupType.EXPECTED }
                    .fold(BigDecimal.ZERO) { acc, g -> acc + g.totalBudget }
                val totalExpectedActual = groupSpendings.filter { it.group.budget.groupType == BudgetGroupType.EXPECTED }
                    .fold(BigDecimal.ZERO) { acc, g -> acc + g.totalActual }

                val netSavings = totalIncome - totalLimitSpent
                val savingsRate = if (totalIncome > BigDecimal.ZERO) {
                    (netSavings.toFloat() / totalIncome.toFloat() * 100f)
                } else 0f

                BudgetOverallSummary(
                    groups = groupSpendings,
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
                    currency = currency,
                    pageWindow = pageWindow
                )
            }
        }
    }


    fun getGroupSpendingAllCurrencies(year: Int, month: Int): Flow<BudgetGroupSpendingRaw> {
        val today = LocalDate.now()
        val ym = YearMonth.of(year, month)
        val monthStart = ym.atDay(1)
        val monthEnd = ym.atEndOfMonth()
        val isCurrentMonth = year == today.year && month == today.monthValue

        return combine(
            budgetDao.getActiveBudgetsWithCategories(),
            userPreferencesRepository.budgetCycleStartDay
        ) { groups, startDay ->
            Pair(groups, startDay)
        }.flatMapLatest { (groups, startDay) ->
            val allWindows = groups.flatMap { group ->
                val windows = windowsForMonth(group.budget, year, month, startDay)
                val displayed = if (isCurrentMonth) {
                    resolveBudgetWindow(group.budget, today, startDay)
                } else {
                    windows.lastOrNull() ?: BudgetWindow(monthStart, monthStart, 0)
                }
                val otherWindows = windows.filter { it != displayed }
                listOf(displayed) + otherWindows
            }.distinct()

            val minStart = allWindows.minOfOrNull { it.start } ?: monthStart
            val maxEnd = allWindows.maxOfOrNull { it.end } ?: monthEnd
            val pageWindowDays = if (minStart != monthStart || maxEnd != monthEnd) {
                java.time.temporal.ChronoUnit.DAYS.between(minStart, maxEnd).toInt() + 1
            } else {
                monthEnd.dayOfMonth
            }
            val pageWindow = BudgetWindow(minStart, maxEnd, pageWindowDays)
            val queryEnd = if (maxEnd.isBefore(monthEnd)) monthEnd else maxEnd
            val queryStart = if (minStart.isAfter(monthStart)) monthStart else minStart

            val daysElapsed: Int
            val daysRemaining: Int
            if (isCurrentMonth) {
                daysElapsed = (java.time.temporal.ChronoUnit.DAYS.between(pageWindow.start, today).toInt() + 1)
                    .coerceIn(1, pageWindow.days)
                daysRemaining = (java.time.temporal.ChronoUnit.DAYS.between(today, pageWindow.end).toInt() + 1)
                    .coerceIn(0, pageWindow.days)
            } else {
                daysElapsed = pageWindow.days
                daysRemaining = 0
            }

            // We also need previous cycle transactions if it's the current month
            val prevCycleQueryStart: LocalDate?
            val prevCycleQueryEnd: LocalDate?
            val firstBudget = groups.firstOrNull()?.budget
            if (isCurrentMonth && firstBudget != null) {
                val currentWindow = resolveBudgetWindow(firstBudget, today, startDay)
                val (prevStart, prevEnd) = BudgetCycle.previousCycle(
                    currentWindow.start to currentWindow.end,
                    startDay
                )
                prevCycleQueryStart = prevStart
                prevCycleQueryEnd = prevEnd
            } else {
                prevCycleQueryStart = null
                prevCycleQueryEnd = null
            }

            val unionMinStart = if (prevCycleQueryStart != null && prevCycleQueryStart.isBefore(queryStart)) prevCycleQueryStart else queryStart
            val unionMaxEnd = if (prevCycleQueryEnd != null && prevCycleQueryEnd.isAfter(queryEnd)) prevCycleQueryEnd else queryEnd

            transactionSplitDao.getTransactionsWithSplitsAllCurrencies(
                unionMinStart.atStartOfDay(),
                unionMaxEnd.atTime(23, 59, 59)
            ).map { unionTxs ->
                val windowed = mutableListOf<WindowSpending>()
                val currentWindows = mutableMapOf<Long, BudgetWindow>()
                val allTransactions = mutableListOf<com.pennywiseai.tracker.data.database.entity.TransactionWithSplits>()

                for (group in groups) {
                    val windows = windowsForMonth(group.budget, year, month, startDay)
                    val displayed = if (isCurrentMonth) {
                        resolveBudgetWindow(group.budget, today, startDay)
                    } else {
                        windows.lastOrNull() ?: BudgetWindow(monthStart, monthStart, 0)
                    }
                    currentWindows[group.budget.id] = displayed
                    
                    val otherWindows = windows.filter { it != displayed }
                    for (w in listOf(displayed) + otherWindows) {
                        val capDate = if (isCurrentWeekInCurrentMonth(w, today, isCurrentMonth)) {
                            today
                        } else {
                            monthEnd
                        }
                        val effectiveEnd = if (capDate.isBefore(w.end)) capDate else w.end
                        
                        val txs = unionTxs.filter { tx ->
                            val d = tx.transaction.dateTime.toLocalDate()
                            !d.isBefore(w.start) && !d.isAfter(effectiveEnd)
                        }
                        
                        windowed.add(WindowSpending(budgetId = group.budget.id, window = w, transactions = txs))
                        allTransactions.addAll(txs)
                    }
                }
                
                val prevCycleTransactions = if (prevCycleQueryStart != null && prevCycleQueryEnd != null) {
                    unionTxs.filter { tx ->
                        val d = tx.transaction.dateTime.toLocalDate()
                        !d.isBefore(prevCycleQueryStart) && !d.isAfter(prevCycleQueryEnd)
                    }
                } else emptyList()

                BudgetGroupSpendingRaw(
                    budgetsWithCategories = groups,
                    windowedSpend = windowed,
                    daysElapsed = daysElapsed,
                    daysRemaining = daysRemaining,
                    today = today,
                    globalStartDay = startDay,
                    isCurrentMonth = isCurrentMonth,
                    prevCycleTransactions = prevCycleTransactions,
                    allTransactions = allTransactions.distinctBy { it.transaction.id },
                    currentWindows = currentWindows
                )
            }
        }
    }

    /**
     * Sum the EXPENSE/INVESTMENT amounts in [transactions]. Used by the
     * per-window spend aggregation. Loan-linked and INCOME/TRANSFER
     * transactions are excluded; the category-based
     * [aggregateBudgetCategorySpending] handles per-category filtering and
     * Refund (DEDUCT_SPENT) deductions at a finer grain.
     */
    private fun sumExpensesForWindow(
        transactions: List<com.pennywiseai.tracker.data.database.entity.TransactionWithSplits>
    ): BigDecimal {
        return transactions.fold(BigDecimal.ZERO) { acc, txWithSplits ->
            val tx = txWithSplits.transaction
            if (tx.transactionType != TransactionType.EXPENSE && tx.transactionType != TransactionType.INVESTMENT) return@fold acc
            if (tx.loanId != null) return@fold acc
            acc + tx.amount
        }
    }

    /**
     * Sum the EXPENSE/INVESTMENT amounts in [transactions] that belong
     * to one of [group]'s categories or type-buckets. The plain
     * [sumExpensesForWindow] (above) is a date-range total — it counts
     * every expense in the window, even ones outside the budget's
     * categories. That mismatch is what made the Budget History page
     * show 43k when the per-category breakdown correctly said 8k. Use
     * this helper for any "what did this budget spend in this window"
     * query the user sees on a card or history row.
     *
     * Falls back to [sumExpensesForWindow] for tracking-all budgets
     * (no per-category allocations) — they include every expense.
     */
    private suspend fun getBudgetWindowFilteredSpend(
        group: BudgetWithCategories,
        transactions: List<com.pennywiseai.tracker.data.database.entity.TransactionWithSplits>
    ): BigDecimal {
        if (group.categories.isEmpty()) return sumExpensesForWindow(transactions)
        val (categoryAmounts, _, typeAmounts) = aggregateBudgetCategorySpending(
            transactions = transactions,
            convertSplit = { _, amount -> amount },
            convertIncome = { tx -> tx.amount }
        )
        val catTotal = categoryAmounts.values.fold(BigDecimal.ZERO) { acc, v -> acc + v }
        val typeTotal = typeAmounts.values.fold(BigDecimal.ZERO) { acc, v -> acc + v }
        return catTotal + typeTotal
    }

    /** Look up the group for [budget], falling back to a synthetic
     *  "tracking all" group if the budget was deleted or has no
     *  current row in the dao. */
    private suspend fun getGroupOrFallback(budget: BudgetEntity): BudgetWithCategories =
        budgetDao.getActiveBudgetsWithCategories().first()
            .firstOrNull { it.budget.id == budget.id }
            ?: BudgetWithCategories(
                budget = budget,
                categories = emptyList()
            )

    /**
     * Build a [BudgetGroupSpending] from the per-window transactions.
     * Splits the window's transactions by category / type, applies the
     * same logic as the original monolithic [buildSummary] but for the
     * budget's specific [displayWindow]. The pace chart is bounded by
     * [displayWindow]; [previousWindows] is rendered as the per-week
     * sub-list on the historical card.
     */
    /**
     * True if [window] contains [today] AND the page is the current month.
     * The current-week flag gates the live-vs-frozen cap in the
     * per-window spend queries. Defensive: a weekly window that
     * straddles the month boundary (e.g. Jun 30..Jul 6) is "current"
     * only when the page is the current month; the June view freezes
     * the Jun 30 portion, the July view counts Jun 30..today live.
     */
    private fun isCurrentWeekInCurrentMonth(
        window: BudgetWindow,
        today: LocalDate,
        isCurrentMonth: Boolean
    ): Boolean = isCurrentMonth &&
        !today.isBefore(window.start) &&
        !today.isAfter(window.end)

    /**
     * Per-window history for a single budget at the selected (year, month).
     * Powers the Budget History screen — one entry per window, with
     * the cap date so the screen can label each row as "Live" or
     * "Frozen as of …".
     *
     * For the current month, the week containing today is live (cap =
     * today); every other week is frozen at the month end. For a
     * historical month, every week is frozen at the month end.
     */
    suspend fun getBudgetHistoryForMonth(
        budget: BudgetEntity,
        year: Int,
        month: Int,
        currency: String
    ): List<PastWindowSpending> {
        val today = LocalDate.now()
        val startDay = userPreferencesRepository.getBudgetCycleStartDay()
        val ym = YearMonth.of(year, month)
        val monthEnd = ym.atEndOfMonth()
        val isCurrentMonth = year == today.year && month == today.monthValue
        val windows = windowsForMonth(budget, year, month, startDay)
        // Resolve the group once so the per-window spend query doesn't
        // re-hit the dao for every window.
        val group = getGroupOrFallback(budget)
        return windows.map { w ->
            val isLive = isCurrentWeekInCurrentMonth(w, today, isCurrentMonth)
            val cap = if (isLive) today else monthEnd
            val effectiveEnd = if (cap.isBefore(w.end)) cap else w.end
            val txs = transactionSplitDao.getTransactionsWithSplitsFiltered(
                w.start.atStartOfDay(),
                effectiveEnd.atTime(23, 59, 59),
                currency
            ).first()
            // Use the per-category-filtered total so the per-row spend
            // matches the per-category breakdown shown in the
            // "View breakdown" bottom sheet (the previous shape used
            // sumExpensesForWindow which counted every EXPENSE in
            // the date range, including ones outside the budget's
            // categories — a 43k row vs 8k breakdown mismatch).
            val spent = getBudgetWindowFilteredSpend(group, txs)
            PastWindowSpending(
                window = w,
                spent = spent,
                capDate = cap,
                isLive = isLive
            )
        }
    }

    /**
     * Per-category breakdown for a single window of a budget. Powers the
     * Budget History "View breakdown" bottom sheet — same shape as the
     * Budget Groups page's per-card category list, but scoped to one
     * window (one week for Weekly, one cycle for Monthly, the literal
     * range for One-time).
     */
    suspend fun getBudgetWindowBreakdown(
        budget: BudgetEntity,
        window: BudgetWindow,
        currency: String
    ): WindowBreakdown {
        val txs = transactionSplitDao.getTransactionsWithSplitsFiltered(
            window.start.atStartOfDay(),
            window.end.atTime(23, 59, 59),
            currency
        ).first()
        val (categoryAmounts, categoryLimitBoosts, typeAmounts) = aggregateBudgetCategorySpending(
            transactions = txs,
            convertSplit = { _, amount -> amount },
            convertIncome = { tx -> tx.amount }
        )
        val groups = budgetDao.getActiveBudgetsWithCategories().first()
        val group = groups.firstOrNull { it.budget.id == budget.id }
        // daysElapsed for the per-row "dailySpend" is the window's day
        // count. It's an integer for the per-row math only; the sheet
        // doesn't care about the historical / current distinction.
        val days = window.days.coerceAtLeast(1)
        val categorySpending = if (group != null) {
            group.categories.map { cat ->
                val actual = if (cat.matchType != null) {
                    typeAmounts[cat.matchType] ?: BigDecimal.ZERO
                } else {
                    categoryAmounts[cat.categoryName] ?: BigDecimal.ZERO
                }
                val boost = if (cat.matchType != null) BigDecimal.ZERO
                    else categoryLimitBoosts[cat.categoryName] ?: BigDecimal.ZERO
                val effectiveBudget = cat.budgetAmount + boost
                val pctUsed = if (effectiveBudget > BigDecimal.ZERO) {
                    (actual.toFloat() / effectiveBudget.toFloat() * 100f).coerceAtLeast(0f)
                } else 0f
                val dailySpend = if (actual > BigDecimal.ZERO) {
                    actual.divide(BigDecimal(days), 0, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO
                BudgetCategorySpending(
                    categoryName = cat.categoryName,
                    budgetAmount = effectiveBudget,
                    actualAmount = actual,
                    percentageUsed = pctUsed,
                    dailySpend = dailySpend
                )
            }
        } else {
            // "Tracking all expenses" budget (no per-cat allocations):
            // show one synthetic row with the total. Match the main
            // card's behaviour for isTrackingAllExpenses = true.
            emptyList()
        }
        val totalBudget = (group?.budget?.limitAmount ?: BigDecimal.ZERO).let { gLimit ->
            if (gLimit > BigDecimal.ZERO) gLimit
            else categorySpending.fold(BigDecimal.ZERO) { acc, c -> acc + c.budgetAmount }
        }
        val totalActual = if (categorySpending.isNotEmpty()) {
            categorySpending.fold(BigDecimal.ZERO) { acc, c -> acc + c.actualAmount }
        } else {
            // No per-cat breakdown → report the window total. For
            // tracking-all budgets the helper falls back to the
            // unfiltered sumExpensesForWindow; for budgets that simply
            // have no spend in this window it returns zero.
            getBudgetWindowFilteredSpend(
                group ?: BudgetWithCategories(budget = budget, categories = emptyList()),
                txs
            )
        }
        return WindowBreakdown(
            window = window,
            categorySpending = categorySpending,
            totalBudget = totalBudget,
            totalActual = totalActual,
            isTrackingAll = group?.categories?.isEmpty() == true
        )
    }

    private suspend fun buildSummaryFromWindows(
        group: BudgetWithCategories,
        displayWindow: BudgetWindow,
        previousWindows: List<PastWindowSpending>,
        transactionsByWindow: Map<BudgetWindow, List<com.pennywiseai.tracker.data.database.entity.TransactionWithSplits>>,
        today: LocalDate,
        isCurrentMonth: Boolean,
        monthStart: LocalDate = displayWindow.start,
        monthEnd: LocalDate = displayWindow.end,
        currency: String,
        displayedCapDate: LocalDate = displayWindow.end,
        displayedIsLive: Boolean = false
    ): BudgetGroupSpending {
        val budget = group.budget
        val displayTxs = transactionsByWindow[displayWindow].orEmpty()

        val totalIncome = displayTxs.fold(BigDecimal.ZERO) { acc, txWithSplits ->
            val tx = txWithSplits.transaction
            if (tx.transactionType != TransactionType.INCOME) acc
            else if (tx.budgetImpactType == BudgetImpactType.DEDUCT_SPENT &&
                tx.budgetCategory != null
            ) acc
            else acc + tx.amount
        }

        val (categoryAmounts, categoryLimitBoosts, typeAmounts) = aggregateBudgetCategorySpending(
            transactions = displayTxs,
            convertSplit = { _, amount -> amount },
            convertIncome = { tx -> tx.amount }
        )

        val totalAllExpenses = categoryAmounts.values.fold(BigDecimal.ZERO) { acc, amount -> acc + amount }

        // Per-budget window timing. For the *current* month, daysElapsed
        // runs from displayWindow.start to today; daysRemaining runs from
        // today to displayWindow.end. For a historical month, the
        // displayed window is fully past, so daysElapsed = window.days
        // and daysRemaining = 0.
        val daysElapsed: Int
        val daysRemaining: Int
        if (isCurrentMonth) {
            daysElapsed = (ChronoUnit.DAYS.between(displayWindow.start, today).toInt() + 1)
                .coerceIn(1, displayWindow.days)
            daysRemaining = (ChronoUnit.DAYS.between(today, displayWindow.end).toInt() + 1)
                .coerceIn(0, displayWindow.days)
        } else {
            daysElapsed = displayWindow.days
            daysRemaining = 0
        }

        fun buildGroupPace(
            categoryNames: Set<String>?,  // null = all categories
            matchTypes: Set<String>,      // transaction-type buckets in this group
            groupBudget: BigDecimal
        ): Pair<List<Double>, List<Double>> {
            if (daysElapsed < 1) return emptyList<Double>() to emptyList()
            val effectiveDays = daysElapsed
            val dailyAmounts = DoubleArray(displayWindow.days)
            displayTxs.forEach { txWithSplits ->
                val tx = txWithSplits.transaction
                if (tx.transactionType == TransactionType.INCOME ||
                    tx.transactionType == TransactionType.TRANSFER ||
                    tx.loanId != null
                ) return@forEach
                val dayIndex = (ChronoUnit.DAYS.between(displayWindow.start, tx.dateTime.toLocalDate()).toInt())
                    .coerceIn(0, displayWindow.days - 1)
                if (tx.transactionType.name in matchTypes) {
                    dailyAmounts[dayIndex] += tx.amount.toDouble()
                } else if (tx.transactionType !in BUDGET_TYPE_BUCKETS) {
                    if (categoryNames == null) {
                        dailyAmounts[dayIndex] += tx.amount.toDouble()
                    } else {
                        txWithSplits.getAmountByCategory().forEach { (cat, amount) ->
                            val catName = cat.ifEmpty { "Others" }
                            if (catName in categoryNames) {
                                dailyAmounts[dayIndex] += amount.toDouble()
                            }
                        }
                    }
                }
            }
            // Subtract Refund (DEDUCT_SPENT) income on the day it occurred.
            displayTxs.forEach { txWithSplits ->
                val tx = txWithSplits.transaction
                if (tx.transactionType != TransactionType.INCOME) return@forEach
                if (tx.budgetImpactType != BudgetImpactType.DEDUCT_SPENT) return@forEach
                val category = tx.budgetCategory ?: return@forEach
                if (categoryNames != null && category !in categoryNames) return@forEach
                val dayIndex = (ChronoUnit.DAYS.between(displayWindow.start, tx.dateTime.toLocalDate()).toInt())
                    .coerceIn(0, displayWindow.days - 1)
                dailyAmounts[dayIndex] -= tx.amount.toDouble()
            }
            val cumulative = mutableListOf<Double>()
            var runningNet = 0.0
            for (i in 0 until effectiveDays) {
                runningNet += dailyAmounts[i]
                cumulative.add(runningNet.coerceAtLeast(0.0))
            }
            val pace = if (groupBudget > BigDecimal.ZERO) {
                val dailyPace = groupBudget.toDouble() / displayWindow.days
                (1..effectiveDays).map { it * dailyPace }
            } else emptyList()
            return cumulative to pace
        }

        val isTrackingAll = group.categories.isEmpty()
        val groupSpending = if (isTrackingAll) {
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
                dailyBudgetPace = budgetPace,
                windowStart = displayWindow.start,
                windowEnd = displayWindow.end,
                windowDays = displayWindow.days,
                periodType = budget.periodType,
                previousWindows = previousWindows,
                displayedCapDate = displayedCapDate,
                displayedIsLive = displayedIsLive
            )
        } else {
            val catSpending = group.categories.map { cat ->
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
            val catNames = group.categories.filter { it.matchType == null }.map { it.categoryName }.toSet()
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
                dailyBudgetPace = budgetPace,
                windowStart = displayWindow.start,
                windowEnd = displayWindow.end,
                windowDays = displayWindow.days,
                periodType = budget.periodType,
                previousWindows = previousWindows,
                displayedCapDate = displayedCapDate,
                displayedIsLive = displayedIsLive
            )
        }
        return groupSpending
    }

    companion object {
        /** Transaction types that can back a budget "type bucket". */
        val BUDGET_TYPE_BUCKETS = setOf(TransactionType.INVESTMENT)
    }
}

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
        if (type in BudgetGroupRepository.BUDGET_TYPE_BUCKETS) {
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

/**
 * Result of [BudgetGroupRepository.aggregateBudgetCategorySpending] — the
 * three maps the per-budget summary reads from. Kept at top-level so the
 * companion-object function below can name it; both the single-currency
 * and the unified-currency paths use the same shape.
 */
data class CategoryAggregation(
    val categoryAmounts: Map<String, BigDecimal>,
    val categoryLimitBoosts: Map<String, BigDecimal>,
    val typeAmounts: Map<String, BigDecimal>
)

/**
 * Internal 4-tuple used by [BudgetGroupRepository.createGroup] / [updateGroup]
 * to carry the (newStart, newEnd, newWeek, newMonth) tuple back from the
 * period-type `when` expression. Kept local to this file because nothing
 * outside the repo needs to see it.
 */
private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
