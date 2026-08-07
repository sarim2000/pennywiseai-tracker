package com.pennywiseai.shared.data.statement

import com.pennywiseai.shared.data.util.currentTimeMillis

internal fun amountToMinor(amount: String): Long? {
    val normalized = amount.replace(",", "").trim()
    val parts = normalized.split(".")
    if (parts.isEmpty()) return null
    val whole = parts[0].toLongOrNull() ?: return null
    val fraction = when {
        parts.size < 2 -> 0L
        parts[1].length == 1 -> "${parts[1]}0".toLongOrNull() ?: return null
        else -> parts[1].take(2).toLongOrNull() ?: return null
    }
    return whole * 100L + fraction
}

internal fun fallbackTimestamp(): Long = currentTimeMillis()

/**
 * Epoch millis for a civil date/time in IST (statements are Indian bank
 * documents). Days-from-civil per Howard Hinnant's algorithm — commonMain has
 * no java.time. Shared by the PhonePe and Slice parsers.
 */
internal fun istDateToEpochMillis(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long {
    val y = if (month <= 2) year - 1 else year
    val m = if (month <= 2) month + 9 else month - 3
    val daysSinceEpoch = 365L * y + y / 4 - y / 100 + y / 400 + (m * 306 + 5) / 10 + (day - 1) - 719468L
    val timeMillis = (hour * 3600L + minute * 60L) * 1000L
    val istOffsetMillis = 5 * 3600_000L + 30 * 60_000L // IST = UTC+5:30
    return daysSinceEpoch * 86_400_000L + timeMillis - istOffsetMillis
}

internal val STATEMENT_MONTHS = mapOf(
    "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4,
    "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8,
    "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
)
