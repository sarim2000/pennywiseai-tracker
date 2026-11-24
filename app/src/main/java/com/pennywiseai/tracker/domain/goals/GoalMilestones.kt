package com.pennywiseai.tracker.domain.goals

import java.math.BigDecimal

/**
 * The checkpoints a goal's ring marks, in percent of the target. Reaching
 * 100% isn't one of them: the goal's Reached state covers it.
 */
val GOAL_MILESTONES: List<Int> = listOf(25, 50, 75)

/**
 * The milestone (from [GOAL_MILESTONES]) that saving going from [before] to
 * [after] just crossed: the highest one when a single Add passes several, or
 * null when it passed none or took the goal all the way to [target]. All three
 * amounts are in the goal's currency.
 */
fun crossedMilestone(before: BigDecimal, after: BigDecimal, target: BigDecimal): Int? {
    if (target.signum() <= 0 || after >= target) return null
    return GOAL_MILESTONES.lastOrNull { percent ->
        val mark = target.multiply(BigDecimal(percent)).movePointLeft(2)
        before < mark && after >= mark
    }
}
