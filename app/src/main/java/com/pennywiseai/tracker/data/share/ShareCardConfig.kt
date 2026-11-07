package com.pennywiseai.tracker.data.share

import java.time.LocalDate

/**
 * Window the share card summarises.
 *
 * [LAST_MONTH] exists because on the 1st "this month" is nearly empty, and a card showing
 * three transactions is worse than no card. The month periods follow the budget cycle.
 */
enum class SharePeriod {
    THIS_MONTH,
    LAST_MONTH,
    ALL_TIME;

    companion object {
        fun fromName(value: String?): SharePeriod =
            entries.firstOrNull { it.name == value } ?: THIS_MONTH
    }
}

/**
 * Which single figure the card leads with.
 *
 * One, not several. The card is consumed as a ~260px thumbnail in a chat, and at that
 * size only a large number and a few words survive. An earlier version stacked three
 * labelled sections; downscaled, none of them were legible — including the URL, the one
 * element that brings anyone back. Adding information to a canvas that small subtracts
 * from it.
 */
enum class ShareHero {
    /** "312 — tracked. 0 typed." The effort not spent, which is the actual product. */
    TRANSACTIONS,

    /** "4 — subscriptions I'd forgotten." A discovery, so it carries higher share intent. */
    SUBSCRIPTIONS,

    /** "86 — merchants paid." A count about the period that names none of them. */
    MERCHANTS;

    companion object {
        fun fromName(value: String?): ShareHero =
            entries.firstOrNull { it.name == value } ?: TRANSACTIONS
    }
}

/**
 * What the user has chosen to put on their share card.
 *
 * Configurability is the privacy mechanism: only the user knows whether their own figures
 * are boring or sensitive, so they choose and the preview shows the result before anything
 * leaves the device.
 *
 * Categories were dropped as an option — a category name is not a number, and it cannot
 * carry a card at the size this one is actually read at.
 */
data class ShareCardConfig(
    val hero: ShareHero = ShareHero.TRANSACTIONS,
    val period: SharePeriod = SharePeriod.THIS_MONTH,
) {
    /**
     * The hero actually rendered, given how many subscriptions were found.
     *
     * A card leading with "0 subscriptions" isn't worth sending, so it falls back. Both
     * the card and the picker resolve through here rather than each applying their own
     * rule — when only the card did, choosing Subscriptions with none detected highlighted
     * the chip and changed nothing, which reads as a broken control rather than a
     * deliberate substitution.
     */
    fun effectiveHero(subscriptionCount: Int, merchantCount: Int): ShareHero = when {
        hero == ShareHero.SUBSCRIPTIONS && subscriptionCount == 0 -> ShareHero.TRANSACTIONS
        hero == ShareHero.MERCHANTS && merchantCount == 0 -> ShareHero.TRANSACTIONS
        else -> hero
    }
}

/**
 * The exact dates a review is sharing, so its card counts what the review showed: a
 * cycle isn't a calendar month, and a year's label says which months it really covers.
 */
data class ShareWindow(val start: LocalDate, val end: LocalDate, val label: String)
