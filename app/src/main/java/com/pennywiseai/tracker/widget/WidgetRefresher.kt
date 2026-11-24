package com.pennywiseai.tracker.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * One call to refresh every widget that renders transaction-derived data.
 * Invoke after any mutation that adds, edits or removes transactions — SMS
 * ingestion, manual add/edit/delete, bulk operations, undo/restore,
 * analytics-exclusion toggles — so no widget sits on a stale snapshot until
 * its next periodic refresh. Each worker is unique work with REPLACE policy,
 * so rapid successive calls coalesce.
 */
object WidgetRefresher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun refreshTransactionWidgets(context: Context) {
        RecentTransactionsWidgetUpdateWorker.enqueueOneShot(context)
        CategoryPieWidgetUpdateWorker.enqueueOneShot(context)
        BudgetWidgetUpdateWorker.enqueueOneShot(context)
        // Goals: a transaction edit or delete can change a linked contribution.
        GoalWidgetUpdateWorker.enqueueOneShot(context)
        // Next bills: a payment moves a subscription on and pays down a card bill.
        NextBillsWidgetUpdateWorker.enqueueOneShot(context)
    }

    /**
     * Redraws the tracking check-up, which has no snapshot: it reads SMS access and
     * background limits as it draws. Call when those may have changed (the app comes
     * back from Settings) and when a scan ends.
     */
    fun refreshTrackingCheckup(context: Context) {
        val app = context.applicationContext
        recheckTrackingAccess()
        scope.launch {
            try {
                TrackingCheckupWidget().updateAll(app)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("WidgetRefresher", "Couldn't redraw the tracking check-up widget", e)
            }
        }
    }
}
