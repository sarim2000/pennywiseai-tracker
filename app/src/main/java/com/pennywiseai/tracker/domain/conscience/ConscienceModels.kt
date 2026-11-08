package com.pennywiseai.tracker.domain.conscience

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * The only categories the conscience may comment on. An allowlist, not a
 * denylist: medical, rent, education and everything else stay silent by
 * construction.
 */
enum class ConscienceCategory(val builtinKey: String) {
    FOOD("Food & Dining"),
    ENTERTAINMENT("Entertainment"),
    SHOPPING("Shopping");

    companion object {
        fun fromBuiltinKey(key: String?): ConscienceCategory? = entries.firstOrNull { it.builtinKey == key }
    }
}

/**
 * The kinds of comment, in priority order: when several facts hold for one
 * purchase, the first kind with a line left wins.
 */
enum class ConscienceKind {
    POSITIVE,
    REPEAT,
    LATE_NIGHT,
}

/**
 * A transaction reduced to what the detectors read. [merchant] stays in memory:
 * it only spots renewals. A split row yields one purchase per category, all
 * with the row's [transactionId], so counts go by id.
 */
data class ConsciencePurchase(
    val at: LocalDateTime,
    val amount: BigDecimal,
    val currency: String,
    val category: ConscienceCategory,
    val transactionId: Long = 0,
    val merchant: String = "",
    val recurring: Boolean = false,
    /** The whole charge, before splits and shared parts: what a renewal repeats. */
    val charge: BigDecimal = amount,
)

/** What a detector proved from history. Lines only ever state these facts. */
sealed interface ConscienceFact {
    val category: ConscienceCategory
    val kind: ConscienceKind

    /** [count] purchases in the category within the last [hours] hours, this one included. */
    data class Repeat(override val category: ConscienceCategory, val count: Int, val hours: Int) : ConscienceFact {
        override val kind get() = ConscienceKind.REPEAT
    }

    /** A fresh purchase recorded late at night. Not proof of why, or that the user was up. */
    data class LateNight(override val category: ConscienceCategory) : ConscienceFact {
        override val kind get() = ConscienceKind.LATE_NIGHT
    }

    /** Last completed week came in [percent]% under the usual week, same category and currency. */
    data class Positive(override val category: ConscienceCategory, val percent: Int) : ConscienceFact {
        override val kind get() = ConscienceKind.POSITIVE
    }
}
