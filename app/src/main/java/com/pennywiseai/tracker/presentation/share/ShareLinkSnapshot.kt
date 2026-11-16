package com.pennywiseai.tracker.presentation.share

import com.pennywiseai.tracker.data.share.ShareLinkExpiry
import com.pennywiseai.tracker.data.share.ShareSnapshot
import java.util.Locale

/**
 * The card as the share link's web page draws it: the same [ShareCardContent] the image
 * was drawn from, so the page can't show anything the preview didn't.
 *
 * Text is fitted to the server's limits (counted in code points) rather than refused:
 * a long goal name loses its tail with an ellipsis instead of failing the upload. Line
 * breaks become spaces and other control characters go, since the server rejects them.
 */
fun ShareCardContent.toSnapshot(theme: ShareTheme, locale: Locale, expiry: ShareLinkExpiry): ShareSnapshot =
    ShareSnapshot(
        kind = kind.name.lowercase(Locale.ROOT),
        locale = shareLocaleTag(locale),
        theme = theme.name.lowercase(Locale.ROOT),
        title = title.fit(80).ifBlank { "PennyWise" },
        subtitle = subtitle?.fit(140)?.ifBlank { null },
        periodLabel = periodLabel?.fit(60)?.ifBlank { null },
        // The server needs a label and a value on both; one without is dropped, not sent blank.
        hero = hero?.let {
            val label = it.label.fit(60)
            val value = it.value.fit(40)
            if (label.isBlank() || value.isBlank()) null
            else ShareSnapshot.Hero(label, value, it.delta?.fit(30)?.ifBlank { null }, it.sentiment.wire)
        },
        stats = stats.map { ShareSnapshot.Stat(it.label.fit(40), it.value.fit(40), it.sentiment.wire) }
            .filter { it.label.isNotBlank() && it.value.isNotBlank() }
            .take(6),
        chart = chart?.toSnapshot(),
        achievements = achievements.take(24).map {
            ShareSnapshot.Achievement(
                id = it.id,
                title = it.title.fit(60),
                description = it.description.fit(120),
                tier = it.tier.coerceIn(0, 5),
                unlockedOn = it.unlockedOn?.toString(),
            )
        },
        expiresInDays = expiry.days,
    )

private fun ShareChart.toSnapshot(): ShareSnapshot.Chart? {
    val kept = points.take(12)
    if (kept.isEmpty()) return null
    return ShareSnapshot.Chart(
        type = type.name.lowercase(Locale.ROOT),
        points = kept.map {
            val value = if (it.value.isFinite()) it.value else 0.0
            ShareSnapshot.Point(
                label = it.label.fit(24),
                value = if (type == ShareChartType.RING) value.coerceIn(0.0, 1.0) else value,
                display = it.display?.fit(30)?.ifBlank { null },
            )
        },
        // The point the image emphasises, spelled out: the card falls back to the largest
        // bar and the latest point on a line, and the page should stress the same one.
        highlightIndex = when (type) {
            ShareChartType.BARS -> highlightIndex ?: 0
            ShareChartType.LINE -> highlightIndex ?: kept.lastIndex
            ShareChartType.DIVERGING, ShareChartType.RING -> highlightIndex
        }?.takeIf { it in kept.indices },
    )
}

private val Sentiment.wire: String get() = name.lowercase(Locale.ROOT)

/**
 * The language and region only ("ar-SA"), which is all the page needs: its direction
 * comes from the language. Extensions such as a numbering system are left off.
 */
internal fun shareLocaleTag(locale: Locale): String {
    val language = locale.language.lowercase(Locale.ROOT).takeIf { it.matches(LANGUAGE) } ?: return "en"
    val region = locale.country.takeIf { it.matches(REGION) }
    return if (region != null) "$language-$region" else language
}

private val LANGUAGE = Regex("[a-z]{2,3}")
private val REGION = Regex("[A-Z]{2}|[0-9]{3}")

/** At most [max] code points, without control characters; cut text ends in an ellipsis. */
internal fun String.fit(max: Int): String {
    val clean = buildString {
        this@fit.forEach { c ->
            when {
                c == '\n' || c == '\r' || c == '\t' -> append(' ')
                c.code < 0x20 || c.code == 0x7F -> Unit
                else -> append(c)
            }
        }
    }.trim()
    if (clean.codePointCount(0, clean.length) <= max) return clean
    return clean.substring(0, clean.offsetByCodePoints(0, max - 1)).trimEnd() + "…"
}
