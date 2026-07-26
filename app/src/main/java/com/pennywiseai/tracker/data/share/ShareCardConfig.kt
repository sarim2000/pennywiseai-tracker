package com.pennywiseai.tracker.data.share

/** Window the share card summarises. */
enum class SharePeriod {
    THIS_MONTH,
    ALL_TIME;

    companion object {
        fun fromName(value: String?): SharePeriod =
            entries.firstOrNull { it.name == value } ?: THIS_MONTH
    }
}

/**
 * What the user has chosen to put on their share card.
 *
 * Configurability is the privacy mechanism here. An earlier version redacted everything
 * because that was the safest guess about what people would be willing to broadcast —
 * but the user is the only one who knows whether their own categories are boring or
 * sensitive. They choose, and the preview shows them the result before anything leaves
 * the device.
 *
 * At least one section must stay enabled; [enabledSectionCount] is what the card's
 * layout scales against and what the ViewModel guards on.
 */
data class ShareCardConfig(
    val showTransactions: Boolean = true,
    val showCategories: Boolean = true,
    val showSubscriptions: Boolean = true,
    val period: SharePeriod = SharePeriod.THIS_MONTH,
) {
    val enabledSectionCount: Int
        get() = listOf(showTransactions, showCategories, showSubscriptions).count { it }

    val hasAnySection: Boolean get() = enabledSectionCount > 0
}
