package com.pennywiseai.tracker.data.webhook

import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.WebhookRangePreset
import com.pennywiseai.tracker.ui.UiText
import java.net.URI
import java.util.Currency

object WebhookValidation {
    const val MIN_INTERVAL_HOURS = 1
    const val MAX_INTERVAL_HOURS = 24

    fun validateName(name: String): UiText? =
        if (name.isBlank()) UiText.Res(R.string.webhooks_error_name_required) else null

    fun validateUrl(url: String): UiText? {
        val parsed = runCatching { URI(url.trim()) }.getOrNull()
        return when {
            url.isBlank() -> UiText.Res(R.string.webhooks_error_url_required)
            parsed == null || parsed.scheme?.lowercase() !in setOf("http", "https") || parsed.host.isNullOrBlank() ->
                UiText.Res(R.string.webhooks_error_url_invalid)
            parsed.scheme.equals("http", true) && parsed.host.lowercase() !in setOf("localhost", "127.0.0.1") ->
                UiText.Res(R.string.webhooks_error_url_https)
            parsed.userInfo != null || parsed.fragment != null -> UiText.Res(R.string.webhooks_error_url_userinfo)
            else -> null
        }
    }

    fun validateDraft(draft: WebhookProfileDraft): UiText? {
        validateName(draft.name)?.let { return it }
        validateUrl(draft.url)?.let { return it }
        if (runCatching { Currency.getInstance(draft.currency.trim().uppercase()) }.isFailure) return UiText.Res(R.string.webhooks_error_currency)
        if (draft.dataTypes.isEmpty()) return UiText.Res(R.string.webhooks_error_data_types)
        if (draft.rangePreset == WebhookRangePreset.CUSTOM &&
            (draft.customStart == null || draft.customEnd == null || draft.customStart > draft.customEnd)) {
            return UiText.Res(R.string.webhooks_error_date_range)
        }
        val keys = mutableSetOf<String>()
        for (header in draft.headers) {
            val key = header.key.trim()
            if (!key.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+"))) return UiText.Res(R.string.webhooks_error_header_name)
            if (header.value.any { it == '\r' || it == '\n' || it == '\u0000' }) return UiText.Res(R.string.webhooks_error_header_line_breaks)
            if (key.lowercase() in setOf("host", "content-length", "content-type", "connection", "transfer-encoding")) return UiText.Res(R.string.webhooks_error_header_managed)
            if (!keys.add(key.lowercase())) return UiText.Res(R.string.webhooks_error_header_unique)
        }
        return null
    }

    fun validateIntervalHours(text: String): UiText? =
        if (text.toIntOrNull() in MIN_INTERVAL_HOURS..MAX_INTERVAL_HOURS) null
        else UiText.Res(R.string.webhooks_error_interval, listOf(MIN_INTERVAL_HOURS, MAX_INTERVAL_HOURS))
}
