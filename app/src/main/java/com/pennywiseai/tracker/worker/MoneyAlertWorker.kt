package com.pennywiseai.tracker.worker

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.BudgetSummaryProvider
import com.pennywiseai.tracker.domain.spending.MoneyAlertRules
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** Compares budgets and manual balances with what the user was already told. */
@Singleton
class MoneyAlertChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val budgetSummaryProvider: BudgetSummaryProvider,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    /**
     * Each LIMIT budget, as the Budgets screen sees it. With [notify] off this
     * only records what is already true, so turning the alerts on doesn't
     * replay a month of old news.
     */
    suspend fun checkBudgets(notify: Boolean) {
        val today = LocalDate.now()
        val summary = budgetSummaryProvider.summary(YearMonth.from(today)).first()
        for (spending in summary.groups) {
            val budget = spending.group.budget
            if (budget.groupType != BudgetGroupType.LIMIT) continue
            val tier = MoneyAlertRules.budgetTier(
                spending.totalActual, spending.totalBudget, spending.windowStart, spending.windowEnd, today
            ) ?: continue
            val window = "${spending.windowStart}|${spending.windowEnd}|${budget.limitAmount.stripTrailingZeros().toPlainString()}"
            if (userPreferencesRepository.claimBudgetAlert(budget.id, window, tier) && notify) {
                MoneyAlertNotifications.showBudgetAlert(context, spending, tier, summary.currency)
            }
        }
    }

    /**
     * Manual and cash accounts: no SMS reports their balance, so there is no
     * crossing to catch in [com.pennywiseai.tracker.data.manager.SmsTransactionProcessor].
     * One alert per dip, rearmed once the balance is back above the threshold.
     */
    // ponytail: an account already under its threshold gets one true alert on its next transaction; seed it if that annoys.
    suspend fun checkManualBalances() {
        for (account in accountBalanceRepository.getAllLatestBalancesOnce()) {
            val threshold = account.lowBalanceThreshold ?: continue
            if (!accountBalanceRepository.isManualAccount(account.bankName, account.accountLast4)) continue
            val isLow = account.balance <= threshold
            if (userPreferencesRepository.claimLowBalanceAlert("${account.bankName}|${account.accountLast4}", isLow)) {
                MoneyAlertNotifications.showLowBalance(context, account)
            }
        }
    }
}

/**
 * Runs after a new transaction: live, added by hand or from chat, a bill marked
 * paid, or a recurring one booked. Never after a scan, an edit or a restore:
 * those change the past, they aren't news. A budget they pushed over the limit
 * is reported by the next new transaction, still true. A worker because the
 * unified-currency figures may fetch a rate over the network.
 */
@HiltWorker
class MoneyAlertWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val moneyAlertChecker: MoneyAlertChecker,
    private val userPreferencesRepository: UserPreferencesRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            if (userPreferencesRepository.budgetAlertsEnabled.first()) {
                runCatching { moneyAlertChecker.checkBudgets(notify = true) }
                    .onFailure { Log.e(TAG, "Budget alert check failed", it) }
            }
            runCatching { moneyAlertChecker.checkManualBalances() }
                .onFailure { Log.e(TAG, "Manual balance check failed", it) }
        }
        // Always success: a failure would fail the appended runs behind it.
        return Result.success()
    }

    companion object {
        private const val TAG = "MoneyAlertWorker"
        private const val WORK_NAME = "money_alert_check"

        /** Appended, not replaced: a burst of SMS must neither cancel a run nor skip the newest charge. */
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<MoneyAlertWorker>().build()
            )
        }
    }
}
