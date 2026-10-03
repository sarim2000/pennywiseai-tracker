package com.pennywiseai.tracker.data.webhook

import com.pennywiseai.tracker.data.database.entity.WebhookLogEntity
import com.pennywiseai.tracker.data.database.entity.WebhookLogStatus
import com.pennywiseai.tracker.data.repository.WebhookRepository
import java.time.LocalDateTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

data class WebhookSyncRunResult(val anySuccess: Boolean, val anyRetryableFailure: Boolean)

@Singleton
class WebhookSyncManager @Inject constructor(
    private val repository: WebhookRepository,
    private val builder: WebhookPayloadBuilder,
    private val delivery: WebhookDeliveryService
) {
    private val mutex = Mutex()

    suspend fun syncAll(reason: WebhookSyncReason, test: Boolean = false): WebhookSyncRunResult {
        require(!test) { "Test delivery requires a profile" }
        var success = false
        var retry = false
        repository.enabledProfiles().forEach { profile ->
            val result = syncProfile(profile.id, reason, test)
            success = success || result.anySuccess
            retry = retry || result.anyRetryableFailure
        }
        return WebhookSyncRunResult(success, retry)
    }

    suspend fun syncProfile(id: String, reason: WebhookSyncReason, test: Boolean = false): WebhookSyncRunResult = mutex.withLock {
        val profile = repository.profile(id) ?: return@withLock WebhookSyncRunResult(false, false)
        if (!profile.enabled && !test) return@withLock WebhookSyncRunResult(false, false)
        val types = repository.decodeDataTypes(profile.dataTypes)
        val batches = try {
            builder.build(profile, types, repository.getCursors(id), test)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = "Could not prepare webhook data"
            repository.markFailure(id, message)
            repository.appendLog(WebhookLogEntity(profileId = id, profileName = profile.name,
                syncReason = reason, status = WebhookLogStatus.FAILURE, message = message))
            return@withLock WebhookSyncRunResult(false, false)
        }
        val updates = mutableListOf<WebhookCursorUpdate>()
        var success = false
        var retry = false
        var completed = batches.isNotEmpty()
        for (batch in batches) {
            val attempt = delivery.deliver(profile.url, repository.decodeHeaders(profile.headersJson), batch.envelope)
            repository.appendLog(WebhookLogEntity(
                profileId = profile.id, profileName = profile.name, syncReason = reason,
                status = if (attempt.success) WebhookLogStatus.SUCCESS else WebhookLogStatus.FAILURE,
                message = attempt.message, httpStatus = attempt.httpStatus, batchCount = batch.envelope.batch.count
            ))
            if (!attempt.success) {
                completed = false
                repository.markFailure(id, attempt.message)
                retry = attempt.retryable
                break
            }
            success = true
            if (!test) repository.recordDelivery(profile, batch.envelope.transactions)
            updates += batch.cursorUpdates
        }
        if (completed && !test) repository.markSuccess(profile, LocalDateTime.now(), updates.distinctBy { it.dataType })
        WebhookSyncRunResult(success, retry)
    }
}
