package com.pennywiseai.tracker.domain.goals

import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.ledger.countedAmount
import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.data.repository.GoalStatus
import com.pennywiseai.tracker.domain.review.PeriodDetail
import com.pennywiseai.tracker.domain.spending.CycleAverages
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Whether every goal still in progress in one currency fits what a usual month
 * leaves over in that currency.
 */
data class GoalAffordability(
    val currency: String,
    /** What those goals need set aside a month, together. */
    val needed: BigDecimal,
    val goalCount: Int,
    /** What a usual month leaves over, maybe below zero; null with no income history in [currency]. */
    val surplus: BigDecimal?,
) {
    /** Null when there's nothing to compare with. */
    val fits: Boolean? get() = surplus?.let { it >= needed }

    /** How much more a month the goals need than a usual month leaves; null when they fit or with no data. */
    val shortBy: BigDecimal? get() = surplus?.let { needed - it }?.takeIf { it.signum() > 0 }

    companion object {
        /**
         * Goals in progress only: reached, missed and archived ones ask nothing more a
         * month. A currency the user earns nothing in has no surplus to compare with,
         * so it reads as no data, never as unaffordable. Most goals first.
         * [savedThroughSpending] is added back to the surplus: see [savedThroughSpending].
         */
        fun of(
            goals: List<GoalProgress>,
            averages: Map<String, CycleAverages>,
            savedThroughSpending: Map<String, BigDecimal> = emptyMap(),
        ): List<GoalAffordability> =
            goals.filter { it.status == GoalStatus.IN_PROGRESS }
                .groupBy { it.goal.currency }
                .map { (currency, list) ->
                    GoalAffordability(
                        currency = currency,
                        needed = list.fold(BigDecimal.ZERO) { sum, g -> sum + g.perMonth },
                        goalCount = list.size,
                        surplus = averages[currency]?.takeIf { it.income.signum() > 0 }
                            ?.let { it.surplus + (savedThroughSpending[currency] ?: BigDecimal.ZERO) },
                    )
                }
                .sortedWith(compareByDescending<GoalAffordability> { it.goalCount }.thenBy { it.currency })

        /**
         * Per currency, what a usual month spent that was really saving toward a goal: the
         * shares of spending rows a contribution is linked to (a recurring deposit's debit,
         * say), averaged over the cycles [CycleAverages.of] averages. That money already
         * lowered the surplus, so without adding it back the goals would ask for it twice.
         * Only up to what the row counted as spending: of a 1,000 bill with 800 lent out,
         * 200 lowered the surplus, so a 1,000 contribution from it gives back 200.
         */
        fun savedThroughSpending(detail: PeriodDetail, linked: List<GoalContributionEntity>): Map<String, BigDecimal> {
            val shareOf = linked.filter { it.transactionId != null }
                .groupBy { it.transactionId!! }
                .mapValues { (_, shares) -> shares.fold(BigDecimal.ZERO) { sum, c -> sum + (c.originalAmount ?: c.amount) } }
            if (shareOf.isEmpty()) return emptyMap()
            return detail.cycles.mapNotNull { (currency, cycles) ->
                val averaged = cycles.dropLast(1).takeLast(CycleAverages.CYCLES)
                if (averaged.size < CycleAverages.CYCLES) return@mapNotNull null
                val window = averaged.first().start..averaged.last().end
                val saved = detail.spendRows
                    .map { it.transaction }
                    .filter { it.currency == currency && it.dateTime.toLocalDate() in window }
                    .fold(BigDecimal.ZERO) { sum, t ->
                        val share = shareOf[t.id] ?: return@fold sum
                        sum + share.min(t.countedAmount()).max(BigDecimal.ZERO)
                    }
                if (saved.signum() == 0) null
                else currency to saved.divide(BigDecimal(CycleAverages.CYCLES), 2, RoundingMode.HALF_UP)
            }.toMap()
        }
    }
}
