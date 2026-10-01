package com.pennywiseai.tracker.data.webhook

import android.content.Context
import androidx.work.*
import com.pennywiseai.tracker.data.repository.WebhookRepository
import com.pennywiseai.tracker.worker.WebhookSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebhookSyncScheduler @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: WebhookRepository,
    private val preferences: WebhookPreferences
) {
    private val work = WorkManager.getInstance(context)
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    suspend fun observeSchedule() {
        combine(repository.observeProfiles(), preferences.intervalHours) { profiles, hours ->
            profiles.any { it.enabled } to hours
        }.distinctUntilChanged().collect { (enabled, hours) ->
            if (enabled) {
                work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE,
                    PeriodicWorkRequestBuilder<WebhookSyncWorker>(hours.toLong(), TimeUnit.HOURS)
                        .setConstraints(constraints).build())
            } else {
                work.cancelUniqueWork(WORK_NAME)
            }
        }
    }

    fun enqueue(reason: WebhookSyncReason, test: Boolean = false, profileId: String? = null) {
        require(!test || profileId != null)
        val name = "${ONE_SHOT}_${profileId ?: "all"}_${if (test) "test" else "sync"}"
        work.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<WebhookSyncWorker>().setConstraints(constraints)
                .setInputData(WebhookSyncWorker.inputData(reason, test, profileId)).build())
    }

    companion object {
        const val WORK_NAME = "pennywise_webhook_periodic_sync"
        const val ONE_SHOT = "pennywise_webhook_sync_now"
    }
}
