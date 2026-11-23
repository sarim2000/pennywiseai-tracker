package com.pennywiseai.tracker.widget

import android.content.Context
import com.pennywiseai.tracker.domain.spending.UpcomingPaymentsLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
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
    private val upcomingPaymentsLoader: UpcomingPaymentsLoader,
) {
    /** Runs until cancelled. The day is fixed here: this only spots a change, and the worker reads today. */
    suspend fun watch() {
        val today = LocalDate.now()
        upcomingPaymentsLoader.observe(today, today.plusDays(NextBillsWidgetUpdateWorker.LOOKAHEAD_DAYS), includeOverdue = true)
            .distinctUntilChanged().drop(1).flowOn(Dispatchers.Default)
            .collect { NextBillsWidgetUpdateWorker.enqueueOneShot(context) }
    }
}
