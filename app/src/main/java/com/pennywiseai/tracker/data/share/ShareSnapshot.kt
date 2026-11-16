package com.pennywiseai.tracker.data.share

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Share snapshot v1: the card's content as the web page renders it (`POST /api/shares`,
 * pennywise-web `share/ShareModels.kt`). Every string is already formatted and translated
 * on the phone; the server never formats money or does currency maths. Enum-like fields
 * are the contract's lowercase names.
 */
@Serializable
data class ShareSnapshot(
    val v: Int = VERSION,
    val kind: String,
    val locale: String,
    val theme: String,
    val title: String,
    val subtitle: String?,
    val periodLabel: String?,
    val hero: Hero?,
    val stats: List<Stat>,
    val chart: Chart?,
    val achievements: List<Achievement>,
    /** 7, 30 or 90; null means the link never expires (sent as an explicit null). */
    val expiresInDays: Int?,
) {
    @Serializable
    data class Hero(val label: String, val value: String, val delta: String?, val sentiment: String)

    @Serializable
    data class Stat(val label: String, val value: String, val sentiment: String)

    @Serializable
    data class Chart(val type: String, val points: List<Point>, val highlightIndex: Int?)

    @Serializable
    data class Point(val label: String, val value: Double, val display: String?)

    @Serializable
    data class Achievement(val id: String, val title: String, val description: String, val tier: Int, val unlockedOn: String?)

    companion object {
        const val VERSION = 1

        // Nulls are written out: a missing expiresInDays means 30 days to the server, an
        // explicit null means never.
        private val json = Json { encodeDefaults = true; explicitNulls = true }

        fun encode(snapshot: ShareSnapshot): String = json.encodeToString(serializer(), snapshot)
    }
}

/** How long a link stays up, as offered to the user. */
enum class ShareLinkExpiry(val days: Int?) {
    WEEK(7),
    MONTH(30),
    QUARTER(90),
    NEVER(null);

    companion object {
        val DEFAULT = MONTH
    }
}

/** The server's answer to a publish (`201`). */
@Serializable
data class CreatedShare(
    val id: String,
    val url: String,
    val deleteToken: String,
    /** ISO-8601 instant, seconds precision; null for a link that never expires. */
    val expiresAt: String? = null,
)
