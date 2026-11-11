package com.pennywiseai.tracker.presentation.home

import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.BudgetCategorySpending
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.data.repository.BudgetGroupSpendingRaw
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.data.repository.BudgetWindow
import com.pennywiseai.tracker.data.repository.aggregateBudgetCategorySpending
import com.pennywiseai.tracker.data.ledger.forTotals
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Home's budget carousel in unified mode: every budget's spend and limit converted
 * into [displayCurrency] ([baseCurrency] is the currency limits are stored in), each
 * budget over its own current window. Native mode reads the repository's summary instead.
 *
 * Not the same as the Budgets screen's unified summary (`BudgetSummaryProvider`): that one
 * also bounds income to the page window and fills `pageWindow`.
 */
internal suspend fun homeBudgetSummary(
    raw: BudgetGroupSpendingRaw,
    displayCurrency: String,
    baseCurrency: String,
    convert: ConvertAmount
): BudgetOverallSummary {
    // Per-budget current window for the "X days remaining" math
    // on the home card. The repo's `raw.daysRemaining` is hard-
    // coded to 0 (legacy) so we recompute it from the per-budget
    // current window the repo just resolved — that's how the
    // home pill knows "1 day remaining" is wrong.
    fun daysRemainingFor(budget: BudgetEntity): Int {
        val w = raw.currentWindows[budget.id] ?: return 0
        val today = raw.today
        return (ChronoUnit.DAYS.between(today, w.end).toInt() + 1)
            .coerceIn(0, w.days)
    }
    fun daysElapsedFor(budget: BudgetEntity): Int {
        val w = raw.currentWindows[budget.id] ?: return 0
        val today = raw.today
        return (ChronoUnit.DAYS.between(w.start, today).toInt() + 1)
            .coerceIn(1, w.days)
    }

    // Exclude a Refund from totalIncome only when it's also being subtracted
    // from a category by aggregateBudgetCategorySpending (categorised refund);
    // orphaned DEDUCT_SPENT income stays in the total so netSavings doesn't
    // understate.
    val analyticsTransactions = raw.allTransactions.forTotals()
    var totalIncome = BigDecimal.ZERO
    for (txWithSplits in analyticsTransactions) {
        val tx = txWithSplits.transaction
        if (tx.transactionType != TransactionType.INCOME) continue
        if (tx.budgetImpactType == BudgetImpactType.DEDUCT_SPENT &&
            tx.budgetCategory != null
        ) continue
        totalIncome = totalIncome.add(
            convert(tx.amount, tx.currency, displayCurrency)
                ?: continue // never-rated pair — skip, don't face-value (#670)
        )
    }

    // Route through the shared aggregator so Refund (DEDUCT_SPENT) and
    // Extra budget (ADD_TO_LIMIT) take effect on the home carousel the same
    // way they do on the Budgets screen and the widget. Per budget, over its
    // own current window: allTransactions spans every budget's windows this
    // month, so a weekly budget would otherwise count earlier weeks too.
    suspend fun spendIn(window: BudgetWindow?) = aggregateBudgetCategorySpending(
        transactions = if (window == null) analyticsTransactions else analyticsTransactions.filter {
            val d = it.transaction.dateTime.toLocalDate()
            !d.isBefore(window.start) && !d.isAfter(window.end)
        },
        convertSplit = { fromCurrency, amount ->
            convert(amount, fromCurrency, displayCurrency)
        },
        convertIncome = { tx ->
            convert(tx.amount, tx.currency, displayCurrency)
        }
    )

    val groupSpendingList = raw.budgetsWithCategories.map { group ->
        val isTrackingAll = group.categories.isEmpty()
        val window = raw.currentWindows[group.budget.id]
        val (categoryAmounts, categoryLimitBoosts) = spendIn(window)
        val daysRemaining = daysRemainingFor(group.budget)
        val daysElapsed = daysElapsedFor(group.budget)
        val catSpending = group.categories.map { cat ->
            val actual = categoryAmounts[cat.categoryName] ?: BigDecimal.ZERO
            val convertedBudget = convert(cat.budgetAmount, baseCurrency, displayCurrency) ?: BigDecimal.ZERO
            val effectiveBudget = convertedBudget +
                (categoryLimitBoosts[cat.categoryName] ?: BigDecimal.ZERO)
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
        val convertedGroupLimit = convert(
            group.budget.limitAmount, baseCurrency, displayCurrency
        ) ?: BigDecimal.ZERO
        // "Category Limits" are optional — the group-level limit is the
        // source of truth when set (including when isTrackingAll), with
        // the per-cat sum as a fallback for budgets that only define
        // per-cat amounts.
        val totalBudget = if (convertedGroupLimit > BigDecimal.ZERO) {
            convertedGroupLimit
        } else {
            catSpending.fold(BigDecimal.ZERO) { acc, c -> acc + c.budgetAmount }
        }
        val totalActual = if (isTrackingAll) {
            categoryAmounts.values.fold(BigDecimal.ZERO) { acc, v -> acc + v }
        } else {
            catSpending.fold(BigDecimal.ZERO) { acc, c -> acc + c.actualAmount }
        }
        val remaining = totalBudget - totalActual
        val pctUsed = if (totalBudget > BigDecimal.ZERO) {
            (totalActual.toFloat() / totalBudget.toFloat() * 100f).coerceAtLeast(0f)
        } else 0f
        val dailyAllowance = if (daysRemaining > 0 && remaining > BigDecimal.ZERO) {
            remaining.divide(BigDecimal(daysRemaining), 0, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO
        BudgetGroupSpending(
            group = group,
            categorySpending = if (isTrackingAll) emptyList() else catSpending,
            totalBudget = totalBudget,
            totalActual = totalActual,
            remaining = remaining,
            percentageUsed = pctUsed,
            dailyAllowance = dailyAllowance,
            daysRemaining = daysRemaining,
            daysElapsed = daysElapsed,
            isTrackingAllExpenses = isTrackingAll,
            // The window the repo resolved, as the single-currency path sets it;
            // without it the window defaults to today..today with 0 days.
            windowStart = window?.start ?: LocalDate.now(),
            windowEnd = window?.end ?: LocalDate.now(),
            windowDays = window?.days ?: 0,
            periodType = group.budget.periodType
        )
    }

    val limitGroups = groupSpendingList.filter { it.group.budget.groupType == BudgetGroupType.LIMIT }
    val targetGroups = groupSpendingList.filter { it.group.budget.groupType == BudgetGroupType.TARGET }
    val expectedGroups = groupSpendingList.filter { it.group.budget.groupType == BudgetGroupType.EXPECTED }

    val totalLimitBudget = limitGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalBudget }
    val totalLimitSpent = limitGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalActual }
    val netSavings = totalIncome - totalLimitSpent
    val savingsRate = if (totalIncome > BigDecimal.ZERO) {
        (netSavings.toFloat() / totalIncome.toFloat() * 100f)
    } else 0f
    val limitRemaining = totalLimitBudget - totalLimitSpent
    // Page-level "daysRemaining" picks the first budget's current
    // window — matches the home carousel's hero card.
    val pageDaysRemaining = groupSpendingList.firstOrNull()?.daysRemaining ?: 0
    val dailyAllowance = if (pageDaysRemaining > 0 && limitRemaining > BigDecimal.ZERO) {
        limitRemaining.divide(BigDecimal(pageDaysRemaining), 0, RoundingMode.HALF_UP)
    } else BigDecimal.ZERO

    return BudgetOverallSummary(
        groups = groupSpendingList,
        totalIncome = totalIncome,
        totalLimitBudget = totalLimitBudget,
        totalLimitSpent = totalLimitSpent,
        totalTargetGoal = targetGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalBudget },
        totalTargetActual = targetGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalActual },
        totalExpectedBudget = expectedGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalBudget },
        totalExpectedActual = expectedGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalActual },
        netSavings = netSavings,
        savingsRate = savingsRate,
        dailyAllowance = dailyAllowance,
        daysRemaining = pageDaysRemaining,
        currency = displayCurrency
    )
}
