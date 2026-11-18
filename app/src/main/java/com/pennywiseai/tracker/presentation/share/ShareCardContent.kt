package com.pennywiseai.tracker.presentation.share

import androidx.compose.runtime.Immutable

/**
 * Exactly what a share card renders, already formatted and translated.
 *
 * The card composables draw this and nothing else, so the same value can be sent as the
 * share-link snapshot and the web page shows what the image showed. Anything a card
 * displays belongs here; anything here is displayed. Money in it was formatted with its
 * currency and `maskIfHidden = false`, because it leaves the phone.
 */
@Immutable
data class ShareCardContent(
    val kind: ShareKind,
    val title: String,
    val subtitle: String? = null,
    val periodLabel: String? = null,
    val hero: ShareCardHero? = null,
    val stats: List<ShareCardStat> = emptyList(),
    val chart: ShareChart? = null,
)

/** Card types. Names are the share contract's `kind`, lowercased. */
enum class ShareKind {
    RECAP,
    SPENDING,
    SAVINGS,
    GOAL,
    BUDGET;

    companion object {
        fun fromName(value: String?): ShareKind? = entries.firstOrNull { it.name == value }
    }
}

/** Good and bad only colour a figure; the words on the card stay factual either way. */
enum class Sentiment { GOOD, BAD, NEUTRAL }

@Immutable
data class ShareCardHero(
    val label: String,
    val value: String,
    val delta: String? = null,
    val sentiment: Sentiment = Sentiment.NEUTRAL,
)

@Immutable
data class ShareCardStat(
    val label: String,
    val value: String,
    val sentiment: Sentiment = Sentiment.NEUTRAL,
)

enum class ShareChartType {
    /** Horizontal bars, largest first. */
    BARS,

    /** A line over time, oldest first. */
    LINE,

    /** One point, value 0..1. */
    RING,

    /** Bars either side of zero: positive toward the end edge, negative toward the start. */
    DIVERGING,
}

@Immutable
data class ShareChartPoint(
    val label: String,
    val value: Double,
    /** The figure printed by the point; null when the user hid it. */
    val display: String? = null,
)

@Immutable
data class ShareChart(
    val type: ShareChartType,
    val points: List<ShareChartPoint>,
    val highlightIndex: Int? = null,
)

/**
 * The card's fixed palettes. Not Material You: the card is seen mostly by people who
 * don't have the app, and one that looks different on every sender's phone isn't
 * recognisable as anything. Names are the share contract's `theme`, lowercased.
 */
enum class ShareTheme { MIDNIGHT, AURORA, PAPER }

/** What the user can choose to show. Each card offers the subset in [ShareKind.details]. */
enum class ShareDetail { AMOUNTS, PERCENTAGES, CATEGORIES, CHART, GOAL_NAME, PACE, PERIOD }

val ShareKind.details: List<ShareDetail>
    get() = when (this) {
        ShareKind.RECAP, ShareKind.SPENDING ->
            listOf(ShareDetail.AMOUNTS, ShareDetail.PERCENTAGES, ShareDetail.CATEGORIES, ShareDetail.PERIOD)
        ShareKind.SAVINGS, ShareKind.BUDGET ->
            listOf(ShareDetail.AMOUNTS, ShareDetail.PERCENTAGES, ShareDetail.CHART, ShareDetail.PERIOD)
        ShareKind.GOAL -> listOf(
            ShareDetail.AMOUNTS, ShareDetail.PERCENTAGES, ShareDetail.CHART,
            ShareDetail.GOAL_NAME, ShareDetail.PACE, ShareDetail.PERIOD,
        )
    }

/**
 * Amounts start off: the user opts in to every figure that says how much. The rest is
 * on, since it says nothing about money on its own.
 */
val DEFAULT_SHARE_DETAILS: Set<ShareDetail> = ShareDetail.entries.toSet() - ShareDetail.AMOUNTS

/** A recap leads with a count; every other card is about a figure, and needs one. */
val ShareKind.needsFigure: Boolean get() = this != ShareKind.RECAP

/**
 * The details actually drawn. A card that needs a figure keeps percentages when the
 * user has turned both figures off — while on another card, say — rather than render a
 * card with nothing on it.
 */
fun effectiveDetails(kind: ShareKind, chosen: Set<ShareDetail>): Set<ShareDetail> {
    val offered = chosen intersect kind.details.toSet()
    val hasFigure = ShareDetail.AMOUNTS in offered || ShareDetail.PERCENTAGES in offered
    return if (kind.needsFigure && !hasFigure) offered + ShareDetail.PERCENTAGES else offered
}
