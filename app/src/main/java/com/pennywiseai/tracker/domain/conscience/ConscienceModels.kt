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

enum class ConscienceVoice { JORDANIAN, ENGLISH }

/** How the voice addresses the user. Asked once on opt-in, never inferred. */
enum class ConscienceAddress { MASCULINE, FEMININE }

enum class ConscienceFrequency { NORMAL, LESS }

/** A transaction that passed eligibility, reduced to what the detectors read. */
data class ConsciencePurchase(
    val at: LocalDateTime,
    val amount: BigDecimal,
    val currency: String,
    val category: ConscienceCategory,
)

/** What a detector proved from history. Lines only ever state these facts. */
sealed interface ConscienceFact {
    val category: ConscienceCategory

    /** The category went quiet for [days] days, then came back today. */
    data class Comeback(override val category: ConscienceCategory, val days: Int) : ConscienceFact

    /** Today's total passed the highest single day of the prior 60 days. */
    data class RecordDay(override val category: ConscienceCategory) : ConscienceFact
}

/** A hand-written line. [id] is stable: the no-repeat log stores it. */
data class ConscienceLine(
    val id: String,
    val masculine: String,
    val feminine: String = masculine,
)
