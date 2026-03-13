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
