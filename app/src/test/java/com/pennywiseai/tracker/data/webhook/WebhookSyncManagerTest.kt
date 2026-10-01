package com.pennywiseai.tracker.data.webhook

import com.pennywiseai.tracker.data.database.entity.*
import com.pennywiseai.tracker.data.repository.WebhookRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class WebhookSyncManagerTest {
    private val repository = mockk<WebhookRepository>(relaxed = true)
    private val builder = mockk<WebhookPayloadBuilder>()
    private val delivery = mockk<WebhookDeliveryService>()
    private val manager = WebhookSyncManager(repository, builder, delivery)
    private val profile = WebhookProfileEntity(id = "profile", name = "Test", url = "https://example.com/hook")
    private val end = LocalDateTime.of(2026, 1, 1, 12, 0)
    private val update = WebhookCursorUpdate(WebhookDataType.TRANSACTIONS, end, end)
    private fun batch(index: Int) = WebhookBatchPayload(WebhookEnvelope(
        generatedAt = end.toString(), app = WebhookAppInfo("Test", "test"),
        profile = WebhookProfileInfo(profile.id, profile.name),
        request = WebhookRequestInfo("since_last_success", end.minusDays(1).toString(), end.toString(), "INR", listOf("transactions")),
        batch = WebhookBatchInfo("batch", index, 2)), listOf(update), 0)

    private fun setup(test: Boolean = false) {
        coEvery { repository.profile(profile.id) } returns profile
        every { repository.decodeDataTypes(any()) } returns setOf(WebhookDataType.TRANSACTIONS)
        coEvery { builder.build(profile, any(), any(), test) } returns listOf(batch(1), batch(2))
    }

    @Test fun `partial failure does not advance any cursor`() = runBlocking {
        setup()
        coEvery { delivery.deliver(any(), any(), any()) } returnsMany listOf(
            WebhookAttemptResult(true, 200, "Delivered"), WebhookAttemptResult(false, 503, "HTTP 503", true))
        val result = manager.syncProfile(profile.id, WebhookSyncReason.MANUAL)
        assertTrue(result.anyRetryableFailure)
        coVerify(exactly = 0) { repository.markSuccess(any(), any(), any()) }
        coVerify(exactly = 1) { repository.markFailure(profile.id, "HTTP 503") }
    }

    @Test fun `all successful batches commit cursors once`() = runBlocking {
        setup()
        coEvery { delivery.deliver(any(), any(), any()) } returns WebhookAttemptResult(true, 200, "Delivered")
        manager.syncProfile(profile.id, WebhookSyncReason.MANUAL)
        coVerify(exactly = 1) { repository.markSuccess(profile, any(), listOf(update)) }
    }

    @Test fun `test targets disabled profile without advancing cursor`() = runBlocking {
        setup(test = true)
        coEvery { repository.profile(profile.id) } returns profile.copy(enabled = false)
        coEvery { builder.build(any(), any(), any(), true) } returns listOf(batch(1))
        coEvery { delivery.deliver(any(), any(), any()) } returns WebhookAttemptResult(true, 200, "Delivered")
        manager.syncProfile(profile.id, WebhookSyncReason.TEST, true)
        coVerify(exactly = 1) { delivery.deliver(profile.url, any(), any()) }
        coVerify(exactly = 0) { repository.markSuccess(any(), any(), any()) }
    }

    @Test fun `disabled profile cannot send real data`() = runBlocking {
        coEvery { repository.profile(profile.id) } returns profile.copy(enabled = false)
        manager.syncProfile(profile.id, WebhookSyncReason.MANUAL)
        coVerify(exactly = 0) { builder.build(any(), any(), any(), any()) }
        coVerify(exactly = 0) { delivery.deliver(any(), any(), any()) }
    }
}
