package com.pennywiseai.tracker.domain.goals

import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.data.repository.GoalStatus
import com.pennywiseai.tracker.utils.Money
import com.pennywiseai.tracker.utils.sumByCurrency
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * What Safe to spend sets aside for goals, when the user asks it to: each goal in
 * progress's monthly saving (its [GoalProgress.perMonth]), for the share of the
 * budget cycle still to run. Per goal currency; whole units, rounded up so it
 * never sets aside too little.
 */
object GoalReserve {

    /** [cycleStart]..[cycleEnd] is the current budget cycle; [today] is inside it. */
    fun of(goals: List<GoalProgress>, today: LocalDate, cycleStart: LocalDate, cycleEnd: LocalDate): List<Money> {
        val cycleDays = ChronoUnit.DAYS.between(cycleStart, cycleEnd) + 1
        val daysLeft = (ChronoUnit.DAYS.between(today, cycleEnd) + 1).coerceIn(0, cycleDays)
        if (cycleDays <= 0 || daysLeft == 0L) return emptyList()
        return goals.filter { it.status == GoalStatus.IN_PROGRESS && it.perMonth.signum() > 0 }
            .sumByCurrency({ it.goal.currency }) { g ->
                g.perMonth.multiply(BigDecimal(daysLeft)).divide(BigDecimal(cycleDays), 0, RoundingMode.UP)
            }
            .values.toList()
    }
}
