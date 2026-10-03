package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.webhook.WebhookHeader
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object WebhookHeaderEncoder {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(headers: List<WebhookHeader>): String =
        json.encodeToString(headers.filter { it.key.isNotBlank() })

    fun decode(headersJson: String): List<WebhookHeader> = runCatching {
        json.decodeFromString<List<WebhookHeader>>(headersJson)
    }.getOrDefault(emptyList())

    /**
     * Returns a copy of [headers] with every value wiped to "". Used when exporting backups so a
     * shared file never leaks bearer tokens / API keys; keys are preserved so a restore can prompt
     * the user to re-enter the values.
     */
    fun sanitizeForExport(headers: List<WebhookHeader>): List<WebhookHeader> =
        headers.map { it.copy(value = "") }
}
