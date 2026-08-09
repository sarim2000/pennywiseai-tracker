package com.pennywiseai.tracker.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class CategoryPieWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = CategoryPieWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        CategoryPieWidgetUpdateWorker.enqueuePeriodicUpdate(context)
        CategoryPieWidgetUpdateWorker.enqueueOneShot(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        CategoryPieWidgetUpdateWorker.cancelPeriodicUpdate(context)
    }
}
