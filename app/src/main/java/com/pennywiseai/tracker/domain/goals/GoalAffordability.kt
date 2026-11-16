package com.pennywiseai.tracker.domain.goals

import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.data.repository.GoalStatus
import com.pennywiseai.tracker.domain.spending.CycleAverages
import java.math.BigDecimal

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
         */
        fun of(goals: List<GoalProgress>, averages: Map<String, CycleAverages>): List<GoalAffordability> =
            goals.filter { it.status == GoalStatus.IN_PROGRESS }
                .groupBy { it.goal.currency }
                .map { (currency, list) ->
                    GoalAffordability(
                        currency = currency,
                        needed = list.fold(BigDecimal.ZERO) { sum, g -> sum + g.perMonth },
                        goalCount = list.size,
                        surplus = averages[currency]?.takeIf { it.income.signum() > 0 }?.surplus,
                    )
                }
                .sortedWith(compareByDescending<GoalAffordability> { it.goalCount }.thenBy { it.currency })
    }
}
