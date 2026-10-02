package com.pennywiseai.tracker.data.repository

import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.*
import com.pennywiseai.tracker.data.database.entity.*
import com.pennywiseai.tracker.data.webhook.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebhookRepository @Inject constructor(
    private val database: PennyWiseDatabase,
    private val profiles: WebhookProfileDao,
    private val logs: WebhookLogDao,
    private val cursors: WebhookCursorDao
) {
    fun observeProfiles(): Flow<List<WebhookProfileEntity>> = profiles.observeAll()
    fun observeLogs(id: String): Flow<List<WebhookLogEntity>> = logs.observe(id)
    suspend fun enabledProfiles() = profiles.enabled()
    suspend fun profile(id: String) = profiles.byId(id)

    suspend fun save(draft: WebhookProfileDraft): String = database.withTransaction {
        require(WebhookValidation.validateDraft(draft) == null) { "Invalid webhook configuration" }
        val now = LocalDateTime.now()
        val id = draft.id ?: UUID.randomUUID().toString()
        val old = draft.id?.let { profiles.byId(it) }
        val entity = WebhookProfileEntity(
            id = id, name = draft.name.trim(), url = draft.url.trim(), enabled = draft.enabled,
            rangePreset = draft.rangePreset, customStart = draft.customStart, customEnd = draft.customEnd,
            dataTypes = draft.dataTypes.joinToString(",") { it.name },
            headersJson = encodeHeaders(draft.headers.map { it.copy(key = it.key.trim()) }),
            currency = draft.currency.trim().uppercase(),
            lastError = old?.lastError, consecutiveFailures = old?.consecutiveFailures ?: 0,
            lastSyncedAt = old?.lastSyncedAt, createdAt = old?.createdAt ?: now, updatedAt = now
        )
        profiles.upsert(entity)
        if (old != null && old.url != entity.url) {
            database.webhookDeliveredTransactionDao().deleteForProfile(id)
        }
        if (old != null && (old.url != entity.url || old.currency != entity.currency ||
                old.rangePreset != entity.rangePreset || old.customStart != entity.customStart || old.customEnd != entity.customEnd)) {
            cursors.deleteForProfile(id)
        }
        id
    }

    suspend fun delete(id: String) = database.withTransaction {
        cursors.deleteForProfile(id)
        profiles.delete(id)
    }
    suspend fun setEnabled(id: String, enabled: Boolean) {
        profiles.byId(id)?.let { profiles.update(it.copy(enabled = enabled, updatedAt = LocalDateTime.now())) }
    }
    suspend fun getCursors(id: String) = cursors.forProfile(id)
    suspend fun appendLog(log: WebhookLogEntity) = database.withTransaction {
        if (profiles.byId(log.profileId) == null) return@withTransaction
        logs.insert(log)
        logs.trim()
    }
    suspend fun markSuccess(profile: WebhookProfileEntity, at: LocalDateTime, updates: List<WebhookCursorUpdate>) = database.withTransaction {
        val current = profiles.byId(profile.id) ?: return@withTransaction
        // An edit while delivery is in flight must not commit the old endpoint's cursors.
        if (!current.enabled || !matchesDelivery(current, profile)) return@withTransaction
        profiles.markSuccess(profile.id, at)
        updates.forEach { cursors.upsert(WebhookCursorEntity(profile.id, it.dataType, it.successAt, it.rangeEnd, at)) }
    }
    suspend fun recordDelivery(profile: WebhookProfileEntity, transactions: List<WebhookTransactionPayload>) = database.withTransaction {
        val current = profiles.byId(profile.id) ?: return@withTransaction
        // The receiver already accepted this batch, even if delivery was disabled or filters changed meanwhile.
        if (current.url != profile.url) return@withTransaction
        val receipts = database.webhookDeliveredTransactionDao()
        transactions.forEach { transaction ->
            val id = transaction.id.removePrefix("txn_").toLong()
            if (transaction.action == "delete") receipts.delete(profile.id, id)
            else receipts.insert(WebhookDeliveredTransactionEntity(profile.id, id))
        }
    }
    private fun matchesDelivery(current: WebhookProfileEntity, sent: WebhookProfileEntity): Boolean =
        current.url == sent.url && current.currency == sent.currency &&
            current.rangePreset == sent.rangePreset && current.customStart == sent.customStart &&
            current.customEnd == sent.customEnd && current.dataTypes == sent.dataTypes &&
            current.headersJson == sent.headersJson
    suspend fun markFailure(id: String, error: String) = profiles.markFailure(id, error, LocalDateTime.now())
    fun decodeHeaders(json: String) = WebhookHeaderEncoder.decode(json)
    fun encodeHeaders(headers: List<WebhookHeader>) = WebhookHeaderEncoder.encode(headers)
    fun decodeDataTypes(value: String): Set<WebhookDataType> = value.split(',').mapNotNull {
        runCatching { WebhookDataType.valueOf(it) }.getOrNull()
    }.toSet()
}
