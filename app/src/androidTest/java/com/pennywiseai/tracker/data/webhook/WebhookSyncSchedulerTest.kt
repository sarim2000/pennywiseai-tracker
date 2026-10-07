package com.pennywiseai.tracker.data.webhook

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.data.database.entity.WebhookProfileEntity
import com.pennywiseai.tracker.data.repository.WebhookRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebhookSyncSchedulerTest {
    @Test fun hydrationPreservesWorkAndRevocationCancelsIt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        val work = WorkManager.getInstance(context)
        val profiles = MutableStateFlow(listOf(WebhookProfileEntity(
            id = "schedule-test", name = "Test", url = "https://example.com/hook")))
        val pro = MutableStateFlow<Boolean?>(true)
        val repository = mockk<WebhookRepository> { every { observeProfiles() } returns profiles }
        val preferences = mockk<WebhookPreferences> { every { intervalHours } returns MutableStateFlow(6) }
        val gate = mockk<EntitlementGate> { every { resolvedProEntitlement } returns pro }
        val scheduler = WebhookSyncScheduler(context, repository, preferences, gate)
        val observation = launch { scheduler.observeSchedule() }
        suspend fun awaitWork(state: WorkInfo.State): WorkInfo = withTimeout(10_000) {
            var info: WorkInfo? = null
            while (info == null) {
                info = work.getWorkInfosForUniqueWork(WebhookSyncScheduler.WORK_NAME).get()
                    .firstOrNull { it.state == state }
                if (info == null) delay(25)
            }
            info
        }
        try {
            val original = awaitWork(WorkInfo.State.ENQUEUED)
            pro.value = null
            delay(100)
            assertEquals(original.id, awaitWork(WorkInfo.State.ENQUEUED).id)
            pro.value = true
            delay(100)
            assertEquals(original.id, awaitWork(WorkInfo.State.ENQUEUED).id)
            pro.value = false
            assertEquals(original.id, awaitWork(WorkInfo.State.CANCELLED).id)
        } finally {
            observation.cancelAndJoin()
            work.cancelUniqueWork(WebhookSyncScheduler.WORK_NAME).result.get()
        }
    }
}
