package com.pennywiseai.tracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * "Add to home screen" inside the app: asks the launcher to place one of our
 * widgets, so it can be added without going through the launcher's widget list.
 */
object WidgetPinning {

    /** Offer the action only where the launcher can pin widgets. */
    fun isSupported(context: Context): Boolean =
        AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    /** Budget Overview: nothing to set up, the launcher just places it. */
    fun pinBudgetWidget(context: Context): Boolean = request(context, BudgetWidgetReceiver::class.java, callback = null)

    /** The Challenge widget: it follows whichever challenge is running, so nothing to set up. */
    fun pinChallengeWidget(context: Context): Boolean = request(context, ChallengeWidgetReceiver::class.java, callback = null)

    /**
     * The 2×2 goal widget on [goalId]. A pinned widget skips the launcher's
     * configure step, so [GoalWidgetPinnedReceiver] stores the goal once it's placed.
     */
    fun pinGoalWidget(context: Context, goalId: Long): Boolean {
        val callback = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, GoalWidgetPinnedReceiver::class.java)
                // Keeps each goal's PendingIntent distinct; extras alone don't.
                .setData("pennywise-widget://pin-goal/$goalId".toUri())
                .putExtra(GoalWidgetPinnedReceiver.EXTRA_GOAL_ID, goalId),
            // Mutable: the launcher fills in the new widget's EXTRA_APPWIDGET_ID.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return request(context, GoalWidgetReceiver::class.java, callback)
    }

    private fun request(context: Context, receiver: Class<*>, callback: PendingIntent?): Boolean = try {
        AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, receiver), null, callback)
    } catch (e: IllegalStateException) {
        // Thrown when the app isn't in the foreground; the menu is only shown while it is.
        Log.w("WidgetPinning", "Launcher refused to pin a widget", e)
        false
    }
}

/** Points a goal widget pinned from a goal's screen at that goal, once the launcher has placed it. */
class GoalWidgetPinnedReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val goalId = intent.getLongExtra(EXTRA_GOAL_ID, -1L)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || goalId < 0) return
        val provider = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider
        if (provider != ComponentName(context, GoalWidgetReceiver::class.java)) return

        val pendingResult = goAsync()
        scope.launch {
            try {
                setGoalWidgetGoal(context, appWidgetId, goalId)
            } catch (e: Exception) {
                Log.e("GoalWidgetPinned", "Failed to store the pinned widget's goal", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_GOAL_ID = "com.pennywiseai.tracker.widget.EXTRA_GOAL_ID"
    }
}
