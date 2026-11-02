package com.pennywiseai.tracker.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.pennywiseai.tracker.worker.DailySummaryWorker

/**
 * Debug-only (never in release builds): posts the evening recap now instead of
 * at 9 PM, so it can be checked on a physical phone. The recap must be on in
 * Settings. Guarded by android.permission.DUMP like SmsProbeReceiver:
 *   adb shell am broadcast -n <pkg>/com.pennywiseai.tracker.debug.DailySummaryProbeReceiver
 */
class DailySummaryProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<DailySummaryWorker>()
                .setInputData(workDataOf(DailySummaryWorker.KEY_FORCE to true))
                .build()
        )
    }
}
