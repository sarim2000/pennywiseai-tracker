package com.pennywiseai.tracker.data.webhook

import kotlinx.serialization.json.Json

internal object WebhookPayloadEncoding {
    const val MAX_BYTES = 1024 * 1024
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    fun encode(payload: WebhookEnvelope): ByteArray =
        json.encodeToString(WebhookEnvelope.serializer(), payload).toByteArray(Charsets.UTF_8)
}
