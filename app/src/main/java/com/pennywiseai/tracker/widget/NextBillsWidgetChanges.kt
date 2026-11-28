package com.pennywiseai.tracker.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.pennywiseai.tracker.domain.spending.UpcomingPaymentsLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

/**
 * Rebuilds the Next bills widget whenever the bills it lists change, read from the
 * loader its worker uses so nothing is missed: a subscription or recurring payment
 * added, edited, paid or paused, a card's bill entered or cleared (no transaction is
 * written for that), an account moved to another profile or ignored, the selected
 * profile switched.
 */
class NextBillsWidgetChanges @Inject constructor(
    @ApplicationContext private val context: Context,
    // Lazy: building the loader opens SharedPreferences (IgnoredAccountsStore), which
    // must not happen on the main thread while the Application is injected.
    private val upcomingPaymentsLoader: dagger.Lazy<UpcomingPaymentsLoader>,
) {
    /**
     * Runs until cancelled. Returns at once when no Next bills widget is placed, so users
     * without one don't pay for the bill walk; one placed mid-session is kept fresh by
     * `onUpdate`, [WidgetRefresher] and the 30-minute period until the next app start.
     */
    suspend fun watch() {
        withContext(Dispatchers.Default) {
            if (GlanceAppWidgetManager(context).getGlanceIds(NextBillsWidget::class.java).isEmpty()) return@withContext
            // Fixed for the process: this only spots a change, the worker reads today, and
            // the 30-minute period covers a date change.
            val today = LocalDate.now()
            upcomingPaymentsLoader.get()
                .observe(today, today.plusDays(NextBillsWidgetUpdateWorker.LOOKAHEAD_DAYS), includeOverdue = true)
                .distinctUntilChanged().drop(1)
                .collect { NextBillsWidgetUpdateWorker.enqueueOneShot(context) }
        }
    }
}
