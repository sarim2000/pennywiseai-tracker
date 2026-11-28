package com.pennywiseai.tracker.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.pennywiseai.tracker.MainActivity
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.dao.TransactionGroupDao
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import com.pennywiseai.tracker.data.preferences.NotificationPreferences
import com.pennywiseai.tracker.data.trip.TripNudges
import com.pennywiseai.tracker.data.trip.isOpenEndedRunning
import com.pennywiseai.tracker.data.trip.tripRunningDays
import com.pennywiseai.tracker.receiver.TripNudgeActionReceiver
import com.pennywiseai.tracker.utils.bidiIsolate
import com.pennywiseai.tracker.utils.inAppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks "Still on your <trip>?" once, 14 days into a trip with no end date. Run by the daily
 * [BillReminderWorker] rather than a job of its own. Also the [TripNudges] the repository calls
 * when a trip stops being open-ended, so a pending question goes away at once.
 */
@Singleton
class TripNudgeChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val groupDao: TransactionGroupDao,
    private val preferences: NotificationPreferences
) : TripNudges {

    suspend fun check(now: LocalDateTime = LocalDateTime.now()) {
        for (trip in groupDao.getAllTrips()) {
            if (!trip.isOpenEndedRunning(now)) {
                // Stopped, dated or restarted since it was asked: nothing is pending any more.
                clear(trip.id)
                continue
            }
            if (!TripNudgeNotifications.canShow(context)) continue
            // ponytail: claimed before posting, so a failed post is not retried; one missed nudge is cheaper than a repeat.
            if (preferences.claimTripNudge(trip, now)) TripNudgeNotifications.show(context, trip, tripRunningDays(trip, now))
        }
    }

    override suspend fun clear(tripId: Long) {
        preferences.clearTripNudge(tripId)
        TripNudgeNotifications.cancel(context, tripId)
    }
}

/** The trip question on its own channel, so it can be silenced from the system's settings like Bill reminders. */
object TripNudgeNotifications {
    private const val CHANNEL_ID = "trip_nudges"
    private const val TAG = "trip_nudge"

    private fun manager(context: Context): NotificationManager {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val res = context.inAppLanguage()
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, res.getString(R.string.trip_nudge_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = res.getString(R.string.trip_nudge_channel_description) }
        )
        return manager
    }

    /** False when the user switched the channel off: the nudge is then left unclaimed, not spent. */
    fun canShow(context: Context): Boolean =
        manager(context).getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE

    fun cancel(context: Context, tripId: Long) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(TAG, tripId.toInt())
    }

    fun show(context: Context, trip: TransactionGroupEntity, days: Int) {
        val res = context.inAppLanguage()
        val id = trip.id.toInt()
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // The data URI keeps each trip's PendingIntent apart.
            data = "pennywise-alert://trip/${trip.id}".toUri()
            putExtra(MainActivity.EXTRA_OPEN_GROUP_ID, trip.id)
        }
        val text = res.resources.getQuantityString(R.plurals.trip_nudge_text, days, days)
        manager(context).notify(
            TAG,
            id,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(res.getString(R.string.trip_nudge_title, res.bidiIsolate(trip.name)))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                .addAction(0, res.getString(R.string.trip_stop), TripNudgeActionReceiver.intent(context, TripNudgeActionReceiver.ACTION_STOP, trip.id))
                .addAction(0, res.getString(R.string.trip_nudge_keep_going), TripNudgeActionReceiver.intent(context, TripNudgeActionReceiver.ACTION_KEEP_GOING, trip.id))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                // The trip's name stays off the lock screen, like the other alerts (#734).
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()
        )
    }
}
