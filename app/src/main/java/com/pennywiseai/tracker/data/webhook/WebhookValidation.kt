package com.pennywiseai.tracker.data.webhook

import com.pennywiseai.tracker.data.database.entity.WebhookRangePreset
import java.net.URI
import java.util.Currency

object WebhookValidation {
    const val MIN_INTERVAL_HOURS = 1
    const val MAX_INTERVAL_HOURS = 24

    fun validateName(name: String): String? =
        if (name.isBlank()) "Webhook name is required" else null

    fun validateUrl(url: String): String? {
        val parsed = runCatching { URI(url.trim()) }.getOrNull()
        return when {
            url.isBlank() -> "Webhook URL is required"
            parsed == null || parsed.scheme?.lowercase() !in setOf("http", "https") || parsed.host.isNullOrBlank() ->
                "Enter a valid HTTP or HTTPS URL"
            parsed.scheme.equals("http", true) && parsed.host.lowercase() !in setOf("localhost", "127.0.0.1") ->
                "Use HTTPS for remote endpoints. HTTP is only supported for localhost."
            parsed.userInfo != null || parsed.fragment != null -> "URLs cannot contain user information or fragments"
            else -> null
        }
    }

    fun validateDraft(draft: WebhookProfileDraft): String? {
        validateName(draft.name)?.let { return it }
        validateUrl(draft.url)?.let { return it }
        if (runCatching { Currency.getInstance(draft.currency.trim().uppercase()) }.isFailure) return "Enter a valid currency code"
        if (draft.dataTypes.isEmpty()) return "Select at least one data type"
        if (draft.rangePreset == WebhookRangePreset.CUSTOM &&
            (draft.customStart == null || draft.customEnd == null || draft.customStart > draft.customEnd)) {
            return "Enter a valid custom date range"
        }
        val keys = mutableSetOf<String>()
        for (header in draft.headers) {
            val key = header.key.trim()
            if (!key.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+"))) return "Enter a valid header name"
            if (header.value.any { it == '\r' || it == '\n' || it == '\u0000' }) return "Header values cannot contain line breaks"
            if (key.lowercase() in setOf("host", "content-length", "content-type", "connection", "transfer-encoding")) return "This header is managed automatically"
            if (!keys.add(key.lowercase())) return "Header names must be unique"
        }
        return null
    }

    fun validateIntervalHours(text: String): String? =
        if (text.toIntOrNull() in MIN_INTERVAL_HOURS..MAX_INTERVAL_HOURS) null
        else "Enter a number between $MIN_INTERVAL_HOURS and $MAX_INTERVAL_HOURS"
}
