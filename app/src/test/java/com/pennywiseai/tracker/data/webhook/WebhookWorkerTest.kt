package com.pennywiseai.tracker.data.webhook

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.pennywiseai.tracker.worker.WebhookSyncWorker
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WebhookWorkerTest {
    @Test fun `test job routes only to its requested profile`() = runBlocking {
        val parameters = mockk<WorkerParameters>(relaxed = true)
        every { parameters.inputData } returns WebhookSyncWorker.inputData(WebhookSyncReason.TEST, true, "test-profile")
        val manager = mockk<WebhookSyncManager>()
        coEvery { manager.syncProfile("test-profile", WebhookSyncReason.TEST, true) } returns WebhookSyncRunResult(true, false)
        val worker = WebhookSyncWorker(mockk<Context>(relaxed = true), parameters, manager)
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        coVerify(exactly = 0) { manager.syncAll(any(), any()) }
    }

    @Test fun `test job without profile cannot send global financial data`() = runBlocking {
        val parameters = mockk<WorkerParameters>(relaxed = true)
        every { parameters.inputData } returns WebhookSyncWorker.inputData(WebhookSyncReason.TEST, true)
        val manager = mockk<WebhookSyncManager>()
        val worker = WebhookSyncWorker(mockk<Context>(relaxed = true), parameters, manager)
        assertEquals(ListenableWorker.Result.failure(), worker.doWork())
        coVerify(exactly = 0) { manager.syncAll(any(), any()) }
        coVerify(exactly = 0) { manager.syncProfile(any(), any(), any()) }
    }
}
