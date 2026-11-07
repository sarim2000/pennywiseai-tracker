package com.pennywiseai.tracker.domain.conscience

import java.math.BigDecimal
import java.time.DayOfWeek
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
 * How the lines address the user, for languages whose lines mark it. [AUTO]
 * takes Android 14's per-app grammatical gender when the user set one.
 */
enum class ConscienceAddress { AUTO, MASCULINE, FEMININE }

enum class ConscienceFrequency { NORMAL, LESS }

/**
 * The kinds of comment, in priority order: when several facts hold for one
 * purchase, the first kind with a line left wins. [HELLO] is the fallback that
 * keeps the feature from going quiet for weeks.
 */
enum class ConscienceKind {
    POSITIVE,
    COMEBACK,
    REPEAT,
    WEEKLY,
    LATE_NIGHT,
    RECORD_DAY,
    SPIKE,
    HELLO,
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
)

/** What a detector proved from history. Lines only ever state these facts. */
sealed interface ConscienceFact {
    val category: ConscienceCategory
    val kind: ConscienceKind

    /** The category went quiet for [days] days, then came back today. */
    data class Comeback(override val category: ConscienceCategory, val days: Int) : ConscienceFact {
        override val kind get() = ConscienceKind.COMEBACK
    }

    /** Today's total passed the highest single day of the prior 60 days by a clear margin. */
    data class RecordDay(override val category: ConscienceCategory) : ConscienceFact {
        override val kind get() = ConscienceKind.RECORD_DAY
    }

    /** [count] purchases in the category within the last [hours] hours, this one included. */
    data class Repeat(override val category: ConscienceCategory, val count: Int, val hours: Int) : ConscienceFact {
        override val kind get() = ConscienceKind.REPEAT
    }

    /** A fresh purchase recorded late at night. Not proof of why, or that the user was up. */
    data class LateNight(override val category: ConscienceCategory) : ConscienceFact {
        override val kind get() = ConscienceKind.LATE_NIGHT
    }

    /** The last 7 days in this category and currency crossed well above the usual week. */
    data class Spike(override val category: ConscienceCategory) : ConscienceFact {
        override val kind get() = ConscienceKind.SPIKE
    }

    /** Last completed week came in [percent]% under the usual week, same category and currency. */
    data class Positive(
        override val category: ConscienceCategory,
        val percent: Int,
        val weekStartEpochDay: Long,
    ) : ConscienceFact {
        override val kind get() = ConscienceKind.POSITIVE
    }

    /** This category shows up on [weekday] most weeks. */
    data class Weekly(override val category: ConscienceCategory, val weekday: DayOfWeek) : ConscienceFact {
        override val kind get() = ConscienceKind.WEEKLY
    }

    /** Only that a purchase in the category was recorded just now. */
    data class Hello(override val category: ConscienceCategory) : ConscienceFact {
        override val kind get() = ConscienceKind.HELLO
    }
}
