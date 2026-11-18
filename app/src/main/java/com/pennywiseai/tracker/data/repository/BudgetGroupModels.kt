package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import java.math.BigDecimal
import java.time.LocalDate

/**
 * One bucket a budget group tracks when creating/updating it. A category bucket
 * has `matchType == null` and is matched by [name] against transaction category;
 * a type bucket sets [matchType] to a `TransactionType.name` (e.g. "INVESTMENT")
 * and is matched by transaction type, with [name] used only as the display label.
 */
data class BudgetBucketInput(
    val name: String,
    val amount: BigDecimal,
    val matchType: String? = null
)

data class BudgetCategorySpending(
    val categoryName: String,
    val budgetAmount: BigDecimal,
    val actualAmount: BigDecimal,
    val percentageUsed: Float,
    val dailySpend: BigDecimal
)

/**
 * A historical window within the selected month for a Weekly budget.
 * Drives the per-week sub-list inside the expanded BudgetCard, and the
 * per-window list on the Budget History screen.
 *
 * [capDate] is the "as of" date the spend was summed up to:
 *  - For the current month: `today` if the window contains today (live),
 *    or the page's `monthEnd` for older / future weeks in the same
 *    month (frozen at month end).
 *  - For a historical month: the page's `monthEnd` for every window.
 *
 * [isLive] is true when the cap date is `today` and the window still has
 * days left to accumulate spend — the card uses it to show a "Live"
 * badge instead of "Frozen at …".
 *
 * The spend figures are already in the *display* currency — the repo /
 * viewmodel convert per-window before producing the list, so the
 * card can render the amounts as-is.
 */
data class PastWindowSpending(
    val window: BudgetWindow,
    val spent: BigDecimal,
    val capDate: LocalDate = window.end,
    val isLive: Boolean = false
)

/**
 * Per-category breakdown for a single window of a budget, shown in the
 * Budget History screen's "View breakdown" bottom sheet. Same shape as
 * the Budget Groups page's per-card category list, but scoped to one
 * window (one week for Weekly, one cycle for Monthly, the literal range
 * for One-time). Currency is whatever the viewmodel passed in.
 */
data class WindowBreakdown(
    val window: BudgetWindow,
    val categorySpending: List<BudgetCategorySpending>,
    val totalBudget: BigDecimal,
    val totalActual: BigDecimal,
    val isTrackingAll: Boolean = false
)

data class BudgetGroupSpending(
    val group: BudgetWithCategories,
    val categorySpending: List<BudgetCategorySpending>,
    val totalBudget: BigDecimal,
    val totalActual: BigDecimal,
    val remaining: BigDecimal,
    val percentageUsed: Float,
    val dailyAllowance: BigDecimal,
    val daysRemaining: Int,
    val daysElapsed: Int,
    val isTrackingAllExpenses: Boolean = false,
    val dailyCumulativeSpending: List<Double> = emptyList(),
    val dailyBudgetPace: List<Double> = emptyList(),
    /**
     * The window the card is rendering for this budget. For the current
     * month this is the [windowsForMonth] window that contains today.
     * For a historical month this is the most-recent window in the
     * list, intersected with the month bounds.
     */
    val windowStart: LocalDate = LocalDate.now(),
    val windowEnd: LocalDate = LocalDate.now(),
    val windowDays: Int = 0,
    val periodType: BudgetPeriodType = BudgetPeriodType.MONTHLY,
    /**
     * Older windows in the selected month, with their spend. Populated
     * for Weekly budgets (one entry per older week in the month) so the
     * expanded card can show the per-week sub-list. Each entry carries a
     * [PastWindowSpending.capDate] so the UI can label it "Live" or
     * "Frozen at …".
     */
    val previousWindows: List<PastWindowSpending> = emptyList(),
    /**
     * "As of" date the displayed window's spend was summed up to.
     * For the current month: `today` if the displayed window is the
     * current week; the page's month end otherwise. For a historical
     * month: the page's month end. Default matches the displayed
     * window's end so older callers stay valid.
     */
    val displayedCapDate: LocalDate = LocalDate.now(),
    /**
     * True when the displayed window is the current week in the current
     * month and its cap is `today` (live, still accumulating).
     */
    val displayedIsLive: Boolean = false
)

data class BudgetOverallSummary(
    val groups: List<BudgetGroupSpending>,
    val totalIncome: BigDecimal,
    val totalLimitBudget: BigDecimal,
    val totalLimitSpent: BigDecimal,
    val totalTargetGoal: BigDecimal,
    val totalTargetActual: BigDecimal,
    val totalExpectedBudget: BigDecimal,
    val totalExpectedActual: BigDecimal,
    val netSavings: BigDecimal,
    val savingsRate: Float,
    val dailyAllowance: BigDecimal,
    val daysRemaining: Int,
    val currency: String,
    /**
     * The display window for the *whole page* — the window the month
     * selector is showing. For the current month this matches
     * `resolveBudgetWindow(_, today, _)`; for a historical month it's
     * the (clipped) cycle for Monthly budgets or the first/only window
     * for the page's date range. Drives the page-level "X days left"
     * / daily-allowance summary.
     */
    val pageWindow: BudgetWindow = BudgetWindow(LocalDate.now(), LocalDate.now(), 1)
)

/**
 * Per-budget, per-window spend breakdown for the unified-currency path.
 * Keyed on (budgetId, windowStart) — the viewmodel picks the right entry
 * for each budget's displayed window and for each `previousWindows`
 * entry in the historical Weekly sub-list.
 */
data class WindowSpending(
    val budgetId: Long,
    val window: BudgetWindow,
    val transactions: List<com.pennywiseai.tracker.data.database.entity.TransactionWithSplits>
)

data class BudgetGroupSpendingRaw(
    val budgetsWithCategories: List<BudgetWithCategories>,
    val windowedSpend: List<WindowSpending>,
    val daysElapsed: Int,
    val daysRemaining: Int,
    val today: LocalDate,
    val globalStartDay: Int,
    val isCurrentMonth: Boolean,
    /**
     * Transactions in the cycle that ended *just before* the current one
     * (the "vs last cycle" comparison for the home card / widget).
     * Empty for historical months and for non-Monthly budgets. Stays in
     * its native currency — the viewmodel converts per-transaction
     * before aggregating.
     */
    val prevCycleTransactions: List<com.pennywiseai.tracker.data.database.entity.TransactionWithSplits> = emptyList(),
    /**
     * Legacy: flat list of all transactions in the displayed windows for
     * the current month. Kept for the home / widget which still build
     * the per-category breakdown in the viewmodel. Empty by default; the
     * repo populates it for the unified path so the home carousel can
     * show per-category spend without re-querying.
     */
    val allTransactions: List<com.pennywiseai.tracker.data.database.entity.TransactionWithSplits> = emptyList(),
    /**
     * The per-budget *current* window (the budget's own cycle for the
     * current month, the most-recent window in the list for a historical
     * month). Keyed by budget id. Replaces the previous single
     * `displayedWindow` / `daysRemaining` shape so each budget card on
     * the home can use its own cycle for "X days remaining" — including
     * a Weekly budget straddling a month boundary, where the same
     * window shows in both June and July views.
     */
    val currentWindows: Map<Long, BudgetWindow> = emptyMap()
)
