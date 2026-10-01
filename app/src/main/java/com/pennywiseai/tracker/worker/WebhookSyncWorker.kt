package com.pennywiseai.tracker.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.pennywiseai.tracker.data.webhook.WebhookSyncManager
import com.pennywiseai.tracker.data.webhook.WebhookSyncReason
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class WebhookSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val manager: WebhookSyncManager
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val reason = runCatching {
            WebhookSyncReason.valueOf(inputData.getString(KEY_REASON) ?: WebhookSyncReason.INTERVAL.name)
        }.getOrDefault(WebhookSyncReason.INTERVAL)
        val test = inputData.getBoolean(KEY_TEST, false)
        val profileId = inputData.getString(KEY_PROFILE_ID)
        if (test && profileId == null) return Result.failure()
        val result = if (profileId != null) manager.syncProfile(profileId, reason, test) else manager.syncAll(reason)
        return if (result.anyRetryableFailure && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
    }

    companion object {
        const val KEY_PROFILE_ID = "profile_id"
        private const val KEY_REASON = "reason"
        private const val KEY_TEST = "test"
        private const val MAX_RETRIES = 3

        fun inputData(reason: WebhookSyncReason, test: Boolean, profileId: String? = null): Data =
            Data.Builder().putString(KEY_REASON, reason.name).putBoolean(KEY_TEST, test)
                .putString(KEY_PROFILE_ID, profileId).build()
    }
}
