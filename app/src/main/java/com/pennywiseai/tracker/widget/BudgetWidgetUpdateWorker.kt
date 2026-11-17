package com.pennywiseai.tracker.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.data.repository.BudgetSummaryProvider
import com.pennywiseai.tracker.data.repository.budgetPercentUsed
import com.pennywiseai.tracker.domain.model.BudgetCycle
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

@HiltWorker
class BudgetWidgetUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val budgetSummaryProvider: BudgetSummaryProvider,
    private val userPreferencesRepository: UserPreferencesRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val WORK_NAME = "budget_widget_update"
        private const val WORK_NAME_PERIODIC = "budget_widget_update_periodic"

        fun enqueueOneShot(context: Context) {
            val request = OneTimeWorkRequestBuilder<BudgetWidgetUpdateWorker>().build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun enqueuePeriodicUpdate(context: Context) {
            val request = PeriodicWorkRequestBuilder<BudgetWidgetUpdateWorker>(
                30, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }

        fun cancelPeriodicUpdate(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val today = java.time.LocalDate.now()
            val startDay = userPreferencesRepository.getBudgetCycleStartDay()
            // Use today's calendar month, not the cycle's start month —
            // same fix as the home viewmodel. For a user on startDay=25
            // viewing today=Jul 1, cycleStartYm was June, which made
            // isCurrentMonth=false in the repo and the widget treated
            // the current cycle as historical (daysRemaining=0, no
            // daily allowance). The per-budget current window is
            // resolved by resolveBudgetWindow inside the repo
            // regardless of which calendar month the page is on.
            val todayYm = java.time.YearMonth.of(today.year, today.monthValue)
            val prevCycle = BudgetCycle.previousCycle(
                BudgetCycle.currentCycle(today, startDay), startDay
            )
            // Use the end date of the previous cycle to derive the display month.
            // Using prevCycle.first is wrong for non-default start days: e.g.
            // startDay=25 → prevCycle = May 25–Jun 24, first = May, but
            // getGroupSpending(May) resolves to Apr 25–May 24 (two cycles ago).
            // prevCycle.second = Jun 24 → June → getGroupSpending(June) correctly
            // resolves to May 25–Jun 24.
            val prevCycleStartYm = java.time.YearMonth.from(prevCycle.second)

            // The Budgets screen's own figures in either currency mode, so the widget
            // can't disagree with the cards (Refunds, Extra budget, rollover, #670).
            val summary = budgetWidgetData(
                current = budgetSummaryProvider.summary(todayYm).first(),
                previous = budgetSummaryProvider.summary(prevCycleStartYm).first()
            )

            BudgetWidgetDataStore.update(applicationContext, summary)
            BudgetWidget().updateAll(applicationContext)
            // The summary widget leads with what's left of this budget.
            SpendingSummaryWidget().updateAll(applicationContext)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

/**
 * The budget widget's figures: every spending limit in [current] (the Budgets
 * screen's summary) together, and how savings moved since [previous].
 */
internal fun budgetWidgetData(current: BudgetOverallSummary, previous: BudgetOverallSummary): BudgetWidgetData {
    // Rollover can carry every limit down to 0: that's used up, not "no budget".
    val carry = current.groups.filter { it.group.budget.groupType == BudgetGroupType.LIMIT }
        .fold(BigDecimal.ZERO) { acc, g -> acc + g.carry }
    return BudgetWidgetData(
        totalSpent = current.totalLimitSpent,
        totalLimit = current.totalLimitBudget,
        hasLimit = current.totalLimitBudget.signum() > 0 || carry.signum() < 0,
        remaining = current.totalLimitBudget - current.totalLimitSpent,
        percentageUsed = budgetPercentUsed(current.totalLimitSpent, current.totalLimitBudget, carry),
        dailyAllowance = current.dailyAllowance,
        totalIncome = current.totalIncome,
        netSavings = current.netSavings,
        savingsRate = current.savingsRate,
        savingsDelta = current.netSavings - previous.netSavings,
        currency = current.currency
    )
}
