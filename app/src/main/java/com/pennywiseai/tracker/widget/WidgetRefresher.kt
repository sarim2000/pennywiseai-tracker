package com.pennywiseai.tracker.widget

import android.content.Context

/**
 * One call to refresh every widget that renders transaction-derived data.
 * Invoke after any mutation that adds, edits or removes transactions — SMS
 * ingestion, manual add/edit/delete, bulk operations, undo/restore,
 * analytics-exclusion toggles — so no widget sits on a stale snapshot until
 * its next periodic refresh. Each worker is unique work with REPLACE policy,
 * so rapid successive calls coalesce.
 */
object WidgetRefresher {
    fun refreshTransactionWidgets(context: Context) {
        RecentTransactionsWidgetUpdateWorker.enqueueOneShot(context)
        CategoryPieWidgetUpdateWorker.enqueueOneShot(context)
        BudgetWidgetUpdateWorker.enqueueOneShot(context)
    }
}
