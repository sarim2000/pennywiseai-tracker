package com.pennywiseai.tracker.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.pennywiseai.tracker.worker.BillReminderWorker

/**
 * Debug-only (never in release builds): runs the 9 AM "bill due tomorrow" check
 * now — WorkManager won't run periodic work early, even via `cmd jobscheduler
 * run -f`. Same DUMP guard as SmsProbeReceiver:
 *   adb shell am broadcast -n <pkg>/com.pennywiseai.tracker.debug.BillReminderProbeReceiver
 */
class BillReminderProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<BillReminderWorker>().build())
    }
}
