package com.pennywiseai.tracker.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.pennywiseai.tracker.worker.WeeklySummaryWorker

/**
 * Debug-only (never in release builds): posts the weekly recap now. The recap must
 * be on in Settings. Guarded by android.permission.DUMP like SmsProbeReceiver:
 *   adb shell am broadcast -n <pkg>/com.pennywiseai.tracker.debug.WeeklySummaryProbeReceiver [--ez force false]
 * With force false it behaves like a scheduled run, so a delivered week stays quiet.
 */
class WeeklySummaryProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<WeeklySummaryWorker>()
                .setInputData(workDataOf(WeeklySummaryWorker.KEY_FORCE to intent.getBooleanExtra("force", true)))
                .build()
        )
    }
}
