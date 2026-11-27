package com.pennywiseai.tracker.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
     * Redraws every widget from its saved snapshot, without rebuilding the data:
     * for a change to what they mask. A running session recomposes; any other
     * starts one, which reads the mask afresh.
     */
    fun redrawAll(context: Context) {
        val app = context.applicationContext
        scope.launch {
            val launcher = AppWidgetManager.getInstance(app)
            val glance = GlanceAppWidgetManager(app)
            redrawEach(WIDGET_RECEIVERS, { launcher.getAppWidgetIds(ComponentName(app, it)) }) { widget, id ->
                widget.update(app, glance.getGlanceIdBy(id))
            }
        }
    }

    /**
     * Redraws each widget of [receivers] the launcher has placed, by the ids [placedIds]
     * reports. Not updateAll: that only finds receivers Glance has recorded, and a widget
     * set up through its configure screen may never have been. One failure skips only
     * that widget.
     */
    internal suspend fun redrawEach(
        receivers: List<Class<out GlanceAppWidgetReceiver>>,
        placedIds: (Class<out GlanceAppWidgetReceiver>) -> IntArray,
        redraw: suspend (GlanceAppWidget, Int) -> Unit
    ) {
        for (receiver in receivers) {
            val (widget, ids) = try {
                receiver.getDeclaredConstructor().newInstance().glanceAppWidget to placedIds(receiver)
            } catch (e: Exception) {
                Log.w("WidgetRefresher", "Couldn't find the ${receiver.simpleName} widgets", e)
                continue
            }
            for (id in ids) {
                try {
                    redraw(widget, id)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w("WidgetRefresher", "Couldn't redraw ${widget.javaClass.simpleName} $id", e)
                }
            }
        }
    }

    /** Every widget's receiver: what [redrawAll] asks the launcher about. */
    internal val WIDGET_RECEIVERS: List<Class<out GlanceAppWidgetReceiver>> = listOf(
        RecentTransactionsWidgetReceiver::class.java, SpendingSummaryWidgetReceiver::class.java,
        BudgetWidgetReceiver::class.java, CategoryPieWidgetReceiver::class.java, GoalWidgetReceiver::class.java,
        GoalsListWidgetReceiver::class.java, AddTransactionWidgetReceiver::class.java,
        TrackingCheckupWidgetReceiver::class.java, NextBillsWidgetReceiver::class.java
    )

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
