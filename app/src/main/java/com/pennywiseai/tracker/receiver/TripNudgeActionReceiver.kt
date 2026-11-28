package com.pennywiseai.tracker.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pennywiseai.tracker.data.preferences.NotificationPreferences
import com.pennywiseai.tracker.data.repository.TransactionGroupRepository
import com.pennywiseai.tracker.worker.TripNudgeNotifications
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * The "Still on your trip?" notification's actions. Both work with the app closed: the system
 * starts the process for the broadcast and [goAsync] keeps it alive until the write is done.
 * "Stop trip" is the same conditional stop as the screens; "Keep going" asks again in 14 days.
 */
class TripNudgeActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TripNudgeActionReceiverEntryPoint {
        fun transactionGroupRepository(): TransactionGroupRepository
        fun notificationPreferences(): NotificationPreferences
    }

    companion object {
        private const val TAG = "TripNudgeAction"
        const val ACTION_STOP = "com.pennywiseai.tracker.ACTION_TRIP_NUDGE_STOP"
        const val ACTION_KEEP_GOING = "com.pennywiseai.tracker.ACTION_TRIP_NUDGE_KEEP_GOING"
        private const val EXTRA_TRIP_ID = "trip_id"

        fun intent(context: Context, action: String, tripId: Long): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                // Distinct per trip and per action, so neither overwrites the other.
                (tripId * 2 + if (action == ACTION_STOP) 0 else 1).toInt(),
                Intent(context, TripNudgeActionReceiver::class.java).apply {
                    this.action = action
                    putExtra(EXTRA_TRIP_ID, tripId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1L)
        if (tripId == -1L) return
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            TripNudgeActionReceiverEntryPoint::class.java
        )
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                val repository = entryPoint.transactionGroupRepository()
                when (intent.action) {
                    // Conditional UPDATE: a trip already stopped or dated by then is left alone.
                    ACTION_STOP -> repository.stopTrip(tripId)
                    ACTION_KEEP_GOING -> repository.getGroupById(tripId)?.let {
                        entryPoint.notificationPreferences().keepTripGoing(it, LocalDateTime.now())
                    }
                    else -> Log.w(TAG, "Unknown action: ${intent.action}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling a trip nudge action", e)
            } finally {
                TripNudgeNotifications.cancel(context, tripId)
                pendingResult.finish()
            }
        }
    }
}
