package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.domain.review.CycleTotal
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A usual cycle in one currency: the average income and spending of the [CYCLES]
 * complete cycles before the current one. Home's forecast "usual", the runway and
 * goal affordability all read history through [mean], so they agree on what counts.
 */
data class CycleAverages(val currency: String, val income: BigDecimal, val spent: BigDecimal) {
    /** What a usual cycle leaves over; negative when more goes out than comes in. */
    val surplus: BigDecimal get() = income - spent

    companion object {
        const val CYCLES = 3

        /**
         * The mean of the [CYCLES] cycles before this one ([previous], most recent first,
         * null for a cycle with no data), at [scale] places. Null, never zero, unless all
         * [CYCLES] have data: a new user's two months aren't a usual month.
         */
        fun mean(previous: List<BigDecimal?>, scale: Int = 0): BigDecimal? {
            val history = previous.take(CYCLES)
            if (history.size < CYCLES || history.any { it == null }) return null
            return history.fold(BigDecimal.ZERO) { sum, v -> sum + v!! }
                .divide(BigDecimal(CYCLES), scale, RoundingMode.HALF_UP)
        }

        /**
         * Per currency, from [cycles] as [com.pennywiseai.tracker.domain.review.PeriodDetail]
         * holds them: oldest first, ending with the current cycle, which is left out. A
         * currency without [CYCLES] active cycles before this one has no averages.
         */
        fun of(cycles: Map<String, List<CycleTotal>>): Map<String, CycleAverages> =
            cycles.mapNotNull { (currency, list) ->
                val previous = list.dropLast(1).asReversed().map { cycle -> cycle.takeIf { it.hasActivity } }
                val income = mean(previous.map { it?.income }, scale = 2) ?: return@mapNotNull null
                val spent = mean(previous.map { it?.spent }, scale = 2) ?: return@mapNotNull null
                currency to CycleAverages(currency, income, spent)
            }.toMap()
    }
}
