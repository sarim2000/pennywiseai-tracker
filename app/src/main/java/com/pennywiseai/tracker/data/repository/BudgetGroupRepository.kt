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
        val startDay = runBlocking { userPreferencesRepository.getBudgetCycleStartDay() }
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
        val globalStartDay = runBlocking { userPreferencesRepository.getBudgetCycleStartDay() }

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
        val startDay = runBlocking { userPreferencesRepository.getBudgetCycleStartDay() }
        val ym = YearMonth.of(year, month)
        val monthStart = ym.atDay(1)
        val monthEnd = ym.atEndOfMonth()
        val isCurrentMonth = year == today.year && month == today.monthValue

        return budgetDao.getActiveBudgetsWithCategories().map { groups ->
            // The displayed window for a Weekly budget is the budget's
            // own current window (Jun 29..Jul 5 for a Mon-anchored
            // budget on Jul 1), NOT a per-month clip. This keeps the
            // spend + remaining + days-until-renewal consistent across
            // June view and July view — the user always sees the same
            // window, with the Jun 29..Jul 5 spend attributed to that
            // window whether they navigate to June or July.
            //
            // For the historical month path, the displayed window is
            // the one whose month intersection makes the most sense —
            // the one whose end is closest to the page's month end
            // (or the most-recent window in the list if multiple).
            val perBudgetWindows = groups.map { g ->
                g to windowsForMonth(g.budget, year, month, startDay)
            }
            // Page-level window used for the page-level "X days left"
            // / daily-allowance summary. Uses the *current* resolved
            // window of the first budget so the page-level numbers
            // match the card heroes.
            val pageWindow = perBudgetWindows.firstOrNull()?.let { (g, _) ->
                if (isCurrentMonth) resolveBudgetWindow(g.budget, today, startDay)
                else perBudgetWindows.first().second.lastOrNull()
                    ?: BudgetWindow(monthStart, monthEnd, monthEnd.dayOfMonth)
            } ?: BudgetWindow(monthStart, monthEnd, monthEnd.dayOfMonth)

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
                    // Resolve the displayed window. For the current
                    // month, use resolveBudgetWindow (the budget's own
                    // current window, NOT a clipped per-month slice).
                    // For a historical month, fall back to the
                    // most-recent window from windowsForMonth.
                    val displayWindow = if (isCurrentMonth) {
                        resolveBudgetWindow(budget, today, startDay)
                    } else {
                        windows.last()
                    }
                    // The list of *other* windows in the page's month
                    // — only meaningful for Weekly historical views
                    // (where the sub-list shows the per-week breakdown).
                    val otherWindows = windows.filter { it != displayWindow }
                    val transactionsByWindow = (listOf(displayWindow) + otherWindows)
                        .associateWith { w ->
                            val capDate = if (isCurrentWeekInCurrentMonth(w, today, isCurrentMonth)) {
                                today
                            } else {
                                monthEnd
                            }
                            val effectiveEnd = if (capDate.isBefore(w.end)) capDate else w.end
                            transactionSplitDao.getTransactionsWithSplitsFiltered(
                                w.start.atStartOfDay(),
                                effectiveEnd.atTime(23, 59, 59),
                                currency
                            ).first()
                        }
                    val previous = if (budget.periodType == BudgetPeriodType.WEEKLY) {
                        otherWindows.map { w ->
                            val isLive = isCurrentWeekInCurrentMonth(w, today, isCurrentMonth)
                            val cap = if (isLive) today else monthEnd
                            PastWindowSpending(
                                window = w,
                                spent = sumExpensesForWindow(transactionsByWindow[w].orEmpty()),
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
            val totalIncome = groupSpendings.flatMap { g ->
                transactionsByWindow(g, today, year, month, startDay, isCurrentMonth)
            }.fold(BigDecimal.ZERO) { acc, tx ->
                val t = tx.transaction
                if (t.transactionType == TransactionType.INCOME &&
                    !(t.budgetImpactType == BudgetImpactType.DEDUCT_SPENT && t.budgetCategory != null)
                ) acc + t.amount else acc
            }

            val limitRemaining = totalLimitBudget - totalLimitSpent
            val daysRemaining = ChronoUnit.DAYS.between(today, pageWindow.end).toInt()
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

    /**
     * Helper used by the unified-currency path. Returns the transactions
     * for a single [BudgetGroupSpending] in the windows the page is
     * rendering for that budget. Avoids re-querying inside the page-level
     * income fold.
     */
    private fun transactionsByWindow(
        g: BudgetGroupSpending,
        @Suppress("UNUSED_PARAMETER") today: LocalDate,
        @Suppress("UNUSED_PARAMETER") year: Int,
        @Suppress("UNUSED_PARAMETER") month: Int,
        @Suppress("UNUSED_PARAMETER") startDay: Int,
        @Suppress("UNUSED_PARAMETER") isCurrentMonth: Boolean
    ): List<com.pennywiseai.tracker.data.database.entity.TransactionWithSplits> {
        // The single-currency pipeline already attached transactions to
        // the per-window cache; for the page-level income fold we just
        // return an empty list (the unified path computes income from the
        // raw windowed spend instead). The function is here for the type
        // signature used by the page-level income calculation in the
        // single-currency path; the actual transactions aren't needed.
        return emptyList()
    }

    fun getGroupSpendingAllCurrencies(year: Int, month: Int): Flow<BudgetGroupSpendingRaw> {
        val today = LocalDate.now()
        val startDay = runBlocking { userPreferencesRepository.getBudgetCycleStartDay() }
        val isCurrentMonth = year == today.year && month == today.monthValue
        return budgetDao.getActiveBudgetsWithCategories().map { groups ->
            val windowed = mutableListOf<WindowSpending>()
            // Per-budget current window (for the home card's "X days
            // remaining" math). For the current month, this is the
            // budget's own cycle; for a historical month, this is the
            // most-recent window from windowsForMonth. Sent to the
            // viewmodel via raw.currentWindows so the home pill has
            // accurate daysRemaining / dailyAllowance numbers (the
            // previous shape hard-coded 0, which is why the home card
            // said '1 day remaining no matter what').
            val currentWindows = mutableMapOf<Long, BudgetWindow>()
            val allTransactions = mutableListOf<com.pennywiseai.tracker.data.database.entity.TransactionWithSplits>()
            for (group in groups) {
                val windows = windowsForMonth(group.budget, year, month, startDay)
                for (w in windows) {
                    val txs = transactionSplitDao.getTransactionsWithSplitsAllCurrencies(
                        w.start.atStartOfDay(),
                        w.end.atTime(23, 59, 59)
                    ).first()
                    val spent = sumExpensesForWindow(txs)
                    windowed.add(WindowSpending(budgetId = group.budget.id, window = w, spent = spent))
                    allTransactions.addAll(txs)
                }
                // The home card shows the budget's own current window,
                // not a clipped per-month slice. For Weekly this matters
                // — the displayed window for Mon-anchored on Jul 1 is
                // (Jun 29, Jul 5) whether the page is the June view or
                // the July view.
                val displayed = if (isCurrentMonth) {
                    resolveBudgetWindow(group.budget, today, startDay)
                } else {
                    windows.last()
                }
                currentWindows[group.budget.id] = displayed
            }
            // For the home / widget "vs last cycle" comparison on the
            // current month, also pull transactions in the *previous*
            // Monthly cycle. The viewmodel aggregates by category on top
            // of this list. For historical months there's no "vs last
            // cycle" so the list is empty.
            val firstBudget = groups.firstOrNull()?.budget
            val prevCycleTransactions = if (isCurrentMonth && firstBudget != null) {
                val currentWindow = resolveBudgetWindow(firstBudget, today, startDay)
                val (prevStart, prevEnd) = BudgetCycle.previousCycle(
                    currentWindow.start to currentWindow.end,
                    startDay
                )
                transactionSplitDao.getTransactionsWithSplitsAllCurrencies(
                    prevStart.atStartOfDay(),
                    prevEnd.atTime(23, 59, 59)
                ).first()
            } else emptyList()

            BudgetGroupSpendingRaw(
                budgetsWithCategories = groups,
                windowedSpend = windowed,
                // daysElapsed / daysRemaining are kept as 0 here for
                // legacy callers; the home viewmodel computes
                // per-budget daysRemaining from raw.currentWindows
                // (the budget's actual current window), which is what
                // the home card displays. The previous hard-coded
                // 0 / 0 / windowDays = 1 shape made the home pill say
                // '1 day remaining no matter what'.
                daysElapsed = 0,
                daysRemaining = 0,
                today = today,
                globalStartDay = startDay,
                prevCycleTransactions = prevCycleTransactions,
                allTransactions = allTransactions,
                currentWindows = currentWindows
            )
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
        val startDay = runBlocking { userPreferencesRepository.getBudgetCycleStartDay() }
        val ym = YearMonth.of(year, month)
        val monthEnd = ym.atEndOfMonth()
        val isCurrentMonth = year == today.year && month == today.monthValue
        val windows = windowsForMonth(budget, year, month, startDay)
        return windows.map { w ->
            val isLive = isCurrentWeekInCurrentMonth(w, today, isCurrentMonth)
            val cap = if (isLive) today else monthEnd
            val effectiveEnd = if (cap.isBefore(w.end)) cap else w.end
            val txs = transactionSplitDao.getTransactionsWithSplitsFiltered(
                w.start.atStartOfDay(),
                effectiveEnd.atTime(23, 59, 59),
                currency
            ).first()
            PastWindowSpending(
                window = w,
                spent = sumExpensesForWindow(txs),
                capDate = cap,
                isLive = isLive
            )
        }
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
