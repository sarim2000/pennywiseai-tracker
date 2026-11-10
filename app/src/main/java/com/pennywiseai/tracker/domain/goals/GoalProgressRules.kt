package com.pennywiseai.tracker.domain.goals

import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.data.repository.GoalStatus
import java.math.BigDecimal
import java.math.RoundingMode

/** The one status pill a goal card shows. */
sealed interface GoalPill {
    data object Reached : GoalPill
    data class MissedBy(val amount: BigDecimal) : GoalPill
    data class Behind(val amount: BigDecimal) : GoalPill
    data class PerMonth(val amount: BigDecimal) : GoalPill
    data object Archived : GoalPill
}

/**
 * Behind shows only once the goal trails the even pace by at least a month's
 * saving; a day or two without a contribution still reads as on track.
 */
fun GoalProgress.pill(): GoalPill = when (status) {
    GoalStatus.REACHED -> GoalPill.Reached
    GoalStatus.MISSED -> GoalPill.MissedBy(left)
    GoalStatus.ARCHIVED -> GoalPill.Archived
    GoalStatus.IN_PROGRESS ->
        if (behind.signum() > 0 && behind >= perMonth) GoalPill.Behind(behind) else GoalPill.PerMonth(monthlyDisplay())
}

/** The colour role a goal's one-line status takes in compact rows (Home card, widgets). */
@kotlinx.serialization.Serializable
enum class GoalTone { NEUTRAL, SUCCESS, WARNING, ERROR }

fun GoalProgress.tone(): GoalTone = when (pill()) {
    GoalPill.Reached -> GoalTone.SUCCESS
    is GoalPill.MissedBy -> GoalTone.ERROR
    is GoalPill.Behind -> GoalTone.WARNING
    is GoalPill.PerMonth, GoalPill.Archived -> GoalTone.NEUTRAL
}

/** Per month rounded up to a whole unit for display: "₹1,412/mo", and paying it still reaches the target. */
fun GoalProgress.monthlyDisplay(): BigDecimal = perMonth.setScale(0, RoundingMode.CEILING)

/** [amount] / target, uncapped (1.2 = 120%), both in the goal's currency; 0 with no target. */
fun GoalProgress.ratioOf(amount: BigDecimal): Float =
    if (goal.targetAmount.signum() <= 0) 0f
    else amount.divide(goal.targetAmount, 4, RoundingMode.DOWN).toFloat().coerceAtLeast(0f)

/** Saved / target as 0..1 for the ring (capped at a full ring when over). */
fun GoalProgress.fraction(): Float = ratioOf(saved).coerceAtMost(1f)

/** Whole percent saved, floored so 99.6% never shows 100% before it's Reached. */
fun GoalProgress.percent(): Int =
    if (goal.targetAmount.signum() <= 0) 0
    else saved.multiply(BigDecimal(100)).divide(goal.targetAmount, 0, RoundingMode.DOWN).toInt()
