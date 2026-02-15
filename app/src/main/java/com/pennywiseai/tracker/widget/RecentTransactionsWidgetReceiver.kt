package com.pennywiseai.tracker.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class RecentTransactionsWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = RecentTransactionsWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RecentTransactionsWidgetUpdateWorker.enqueuePeriodicUpdate(context)
        RecentTransactionsWidgetUpdateWorker.enqueueOneShot(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        RecentTransactionsWidgetUpdateWorker.cancelPeriodicUpdate(context)
    }
}
