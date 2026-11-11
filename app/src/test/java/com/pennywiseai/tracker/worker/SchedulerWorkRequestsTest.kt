package com.pennywiseai.tracker.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.data.database.entity.WebhookProfileEntity
import com.pennywiseai.tracker.data.repository.WebhookRepository
import com.pennywiseai.tracker.data.webhook.WebhookPreferences
import com.pennywiseai.tracker.data.webhook.WebhookSyncReason
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Pins what the schedulers hand to WorkManager. WorkManager keeps unique work
 * names, tags and input keys across app updates, so these literals must not
 * change when the schedulers move or are refactored.
 */
class SchedulerWorkRequestsTest {

    private val context = mockk<Context>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)

    @Before fun setUp() {
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any()) } returns workManager
    }

    @After fun tearDown() {
        unmockkObject(WorkManager.Companion)
    }

    @Test fun smsScan_replacesUniqueOneTimeWorkWithTheOptimizedReader() {
        val request = slot<OneTimeWorkRequest>()
        val manager = SmsScanManager(context)

        manager.startSmsLoggingScan()

        verify { workManager.enqueueUniqueWork("optimized_sms_reader_work", ExistingWorkPolicy.REPLACE, capture(request)) }
        assertEquals("com.pennywiseai.tracker.worker.OptimizedSmsReaderWorker", request.captured.workSpec.workerClassName)
        assertTrue(request.captured.tags.containsAll(setOf("sms_logging", "optimized_sms_processing")))
        assertEquals(NetworkType.NOT_REQUIRED, request.captured.workSpec.constraints.requiredNetworkType)
    }

    @Test fun smsScan_cancelAndProgressUseTheSameNameAndTag() {
        val manager = SmsScanManager(context)

        manager.cancelSmsScanning()
        manager.getSmsScanWorkInfo()

        verify { workManager.cancelUniqueWork("optimized_sms_reader_work") }
        verify { workManager.getWorkInfosByTagLiveData("optimized_sms_processing") }
    }

    @Test fun webhookEnqueue_keepsUniqueOneShotWorkWithInputData() {
        val request = slot<OneTimeWorkRequest>()
        val scheduler = webhookScheduler(pro = true)

        scheduler.enqueue(WebhookSyncReason.MANUAL, test = true, profileId = "profile-1")

        verify {
            workManager.enqueueUniqueWork("pennywise_webhook_sync_now_profile-1_test", ExistingWorkPolicy.KEEP, capture(request))
        }
        val spec = request.captured.workSpec
        assertEquals("com.pennywiseai.tracker.worker.WebhookSyncWorker", spec.workerClassName)
        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
        assertEquals("MANUAL", spec.input.getString("reason"))
        assertEquals(true, spec.input.getBoolean("test", false))
        assertEquals("profile-1", spec.input.getString("profile_id"))
    }

    @Test fun webhookEnqueue_allProfilesSyncName() {
        val scheduler = webhookScheduler(pro = true)

        scheduler.enqueue(WebhookSyncReason.MANUAL)

        verify { workManager.enqueueUniqueWork("pennywise_webhook_sync_now_all_sync", ExistingWorkPolicy.KEEP, any<OneTimeWorkRequest>()) }
    }

    @Test fun webhookEnqueue_withoutProDoesNothing() {
        val scheduler = webhookScheduler(pro = false)

        scheduler.enqueue(WebhookSyncReason.MANUAL)

        verify(exactly = 0) { workManager.enqueueUniqueWork(any<String>(), any(), any<OneTimeWorkRequest>()) }
    }

    @Test fun webhookSchedule_updatesPeriodicWorkAtTheChosenInterval() = runBlocking {
        val request = slot<PeriodicWorkRequest>()
        val enqueued = CompletableDeferred<Unit>()
        every { workManager.enqueueUniquePeriodicWork(any(), any(), capture(request)) } answers {
            enqueued.complete(Unit)
            mockk<Operation>(relaxed = true)
        }

        observeUntil(webhookScheduler(pro = true, intervalHours = 6), enqueued)

        verify {
            workManager.enqueueUniquePeriodicWork("pennywise_webhook_periodic_sync", ExistingPeriodicWorkPolicy.UPDATE, any())
        }
        val spec = request.captured.workSpec
        assertEquals("com.pennywiseai.tracker.worker.WebhookSyncWorker", spec.workerClassName)
        assertEquals(TimeUnit.HOURS.toMillis(6), spec.intervalDuration)
        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
    }

    @Test fun webhookSchedule_cancelsPeriodicWorkWhenProIsRevoked() = runBlocking {
        val cancelled = CompletableDeferred<Unit>()
        every { workManager.cancelUniqueWork(any()) } answers {
            cancelled.complete(Unit)
            mockk<Operation>(relaxed = true)
        }

        observeUntil(webhookScheduler(pro = false), cancelled)

        verify { workManager.cancelUniqueWork("pennywise_webhook_periodic_sync") }
        verify(exactly = 0) { workManager.enqueueUniquePeriodicWork(any(), any(), any()) }
    }

    @Test fun webhookWorkNames() {
        assertEquals("pennywise_webhook_periodic_sync", WebhookSyncScheduler.WORK_NAME)
        assertEquals("pennywise_webhook_sync_now", WebhookSyncScheduler.ONE_SHOT)
    }

    /** Runs the schedule observer until WorkManager has been called, then stops it. */
    private suspend fun CoroutineScope.observeUntil(scheduler: WebhookSyncScheduler, called: CompletableDeferred<Unit>) {
        val observation = launch(Dispatchers.Default) { scheduler.observeSchedule() }
        try {
            withTimeout(5_000) { called.await() }
        } finally {
            observation.cancelAndJoin()
        }
    }

    private fun webhookScheduler(pro: Boolean, intervalHours: Int = 6): WebhookSyncScheduler {
        val repository = mockk<WebhookRepository> {
            every { observeProfiles() } returns MutableStateFlow(
                listOf(WebhookProfileEntity(id = "profile-1", name = "Test", url = "https://example.com/hook"))
            )
        }
        val preferences = mockk<WebhookPreferences> { every { this@mockk.intervalHours } returns MutableStateFlow(intervalHours) }
        val gate = mockk<EntitlementGate> {
            every { resolvedProEntitlement } returns MutableStateFlow(pro)
            every { isProEntitled } returns MutableStateFlow(pro)
        }
        return WebhookSyncScheduler(context, repository, preferences, gate)
    }
}
