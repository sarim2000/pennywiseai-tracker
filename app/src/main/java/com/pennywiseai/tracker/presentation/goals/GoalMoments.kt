package com.pennywiseai.tracker.presentation.goals

import com.pennywiseai.tracker.data.events.MascotMoment
import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.data.repository.GoalRules
import com.pennywiseai.tracker.utils.Money

/**
 * What the Chat tab hears once the user's own deposit of [entered] (as typed, in its
 * currency) into a goal committed, from the goal's progress just [before] and [after] it
 * (read in the deposit's own DB transaction): [MascotMoment.GoalReached] when it took the
 * goal to its target, otherwise [MascotMoment.GoalContributed] with the amount. Nothing
 * when the goal already held its target (a deposit into a full goal is no milestone and no
 * progress), or for anything but a deposit.
 */
internal fun goalDepositMoment(before: GoalProgress, after: GoalProgress, entered: Money): MascotMoment? = when {
    !entered.isPositive -> null
    before.saved >= before.goal.targetAmount -> null
    GoalRules.reachedBetween(before, after) -> MascotMoment.GoalReached
    else -> MascotMoment.GoalContributed(entered)
}
