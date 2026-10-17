package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.billing.FreeTierLimits
import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Where a Goal stands. An "Active goal" (GLOSSARY.md) is [IN_PROGRESS] or
 * [MISSED]: Missed goals still count, their due date can be moved.
 */
enum class GoalStatus { IN_PROGRESS, MISSED, REACHED, ARCHIVED }

/** A Goal's computed progress. Every amount is in [GoalEntity.currency]. */
data class GoalProgress(
    val goal: GoalEntity,
    val saved: BigDecimal,
    val left: BigDecimal,
    val over: BigDecimal,
    val status: GoalStatus,
    /** Whole months to the due date, rounded up, at least 1. */
    val monthsLeft: Int,
    val perMonth: BigDecimal,
    val behind: BigDecimal,
) {
    val isActive: Boolean get() = status == GoalStatus.IN_PROGRESS || status == GoalStatus.MISSED
}

/** Pure Goal math (#914). One currency per goal, so sums here never mix currencies. */
object GoalRules {

    /**
     * @param contributions the goal's *effective* contributions (soft-deleted
     *   transactions already excluded), signed: withdrawals are negative.
     */
    fun progress(goal: GoalEntity, contributions: List<GoalContributionEntity>, today: LocalDate): GoalProgress {
        val saved = (goal.alreadySaved + contributions.sumOf { it.amount }).max(BigDecimal.ZERO)
        val target = goal.targetAmount
        val left = (target - saved).max(BigDecimal.ZERO)
        val status = when {
            goal.isArchived -> GoalStatus.ARCHIVED
            saved >= target -> GoalStatus.REACHED
            goal.dueDate.isBefore(today) -> GoalStatus.MISSED
            else -> GoalStatus.IN_PROGRESS
        }
        val months = monthsLeft(today, goal.dueDate)
        return GoalProgress(
            goal = goal,
            saved = saved,
            left = left,
            over = (saved - target).max(BigDecimal.ZERO),
            status = status,
            monthsLeft = months,
            // Whole units, rounded up: a suggestion to save, so "₹1,412/mo" not
            // "₹1,411.77/mo", and paying it every month still reaches the target.
            perMonth = left.divide(BigDecimal(months), 0, RoundingMode.UP),
            behind = (expectedByNow(goal, today) - saved).max(BigDecimal.ZERO),
        )
    }

    /** Whole months from [today] to [dueDate], rounded up; 1 in the final month or once past due. */
    fun monthsLeft(today: LocalDate, dueDate: LocalDate): Int {
        if (!dueDate.isAfter(today)) return 1
        val whole = ChronoUnit.MONTHS.between(today, dueDate)
        val rounded = if (today.plusMonths(whole).isBefore(dueDate)) whole + 1 else whole
        return rounded.toInt().coerceAtLeast(1)
    }

    /** Even pace: target × elapsed / total days from creation to due date, clamped to 0..target. */
    fun expectedByNow(goal: GoalEntity, today: LocalDate): BigDecimal {
        val start = goal.createdAt.toLocalDate()
        val total = ChronoUnit.DAYS.between(start, goal.dueDate)
        if (total <= 0) return goal.targetAmount
        val elapsed = ChronoUnit.DAYS.between(start, today).coerceIn(0, total)
        return goal.targetAmount.multiply(BigDecimal(elapsed))
            .divide(BigDecimal(total), 2, RoundingMode.HALF_UP)
            .min(goal.targetAmount)
    }

    /** Soonest due date first, Reached last; Archived goals are left out. */
    fun orderForDisplay(goals: List<GoalProgress>): List<GoalProgress> =
        goals.filter { it.status != GoalStatus.ARCHIVED }
            .sortedWith(compareBy<GoalProgress>({ it.status == GoalStatus.REACHED }, { it.goal.dueDate }, { it.goal.id }))

    /** Free users get [FreeTierLimits.MAX_ACTIVE_GOALS] active goals. Existing goals are never locked. */
    fun canCreateGoal(isPro: Boolean, activeGoalCount: Int): Boolean =
        isPro || activeGoalCount < FreeTierLimits.MAX_ACTIVE_GOALS

    /** How much of a transaction a contribution uses, in the transaction's currency. */
    fun allocation(c: GoalContributionEntity): BigDecimal = (c.originalAmount ?: c.amount).abs()

    /** What's left of [transactionAmount] after [linked] contributions (minus [excludingId]). */
    fun unallocated(transactionAmount: BigDecimal, linked: List<GoalContributionEntity>, excludingId: Long? = null): BigDecimal =
        (transactionAmount.abs() - linked.filter { it.id != excludingId }.sumOf { allocation(it) }).max(BigDecimal.ZERO)

    /**
     * Scales non-negative [amounts] down proportionally so they sum to exactly
     * [cap] (unchanged if they already fit). Largest-remainder rounding at the
     * finest input scale keeps every result ≥ 0 and the sum exact.
     */
    fun capAmounts(amounts: List<BigDecimal>, cap: BigDecimal): List<BigDecimal> {
        val total = amounts.fold(BigDecimal.ZERO, BigDecimal::add)
        if (total <= cap) return amounts
        val scale = maxOf(cap.scale(), amounts.maxOf { it.scale() }, 0)
        if (cap.signum() <= 0) return amounts.map { BigDecimal.ZERO.setScale(scale) }
        val exact = amounts.map { it.multiply(cap).divide(total, scale + 10, RoundingMode.DOWN) }
        val floors = exact.map { it.setScale(scale, RoundingMode.DOWN) }.toMutableList()
        val unit = BigDecimal.ONE.movePointLeft(scale)
        var spare = (cap - floors.fold(BigDecimal.ZERO, BigDecimal::add)).divide(unit).toInt()
        exact.indices.sortedByDescending { exact[it] - floors[it] }.forEach { i ->
            if (spare > 0) { floors[i] = floors[i] + unit; spare-- }
        }
        return floors
    }

    /**
     * Caps [linked] contributions of one transaction to its new amount [cap]:
     * returns only the rows that changed. [GoalContributionEntity.originalAmount]
     * (the transaction share) is scaled; the goal-currency [GoalContributionEntity.amount]
     * follows by the same ratio. Signs are kept, so nothing flips sign.
     */
    fun capContributions(linked: List<GoalContributionEntity>, cap: BigDecimal, now: LocalDateTime): List<GoalContributionEntity> {
        if (linked.isEmpty()) return emptyList()
        val shares = linked.map { allocation(it) }
        val capped = capAmounts(shares, cap.abs())
        return linked.indices.mapNotNull { i ->
            val c = linked[i]
            val old = shares[i]
            val new = capped[i]
            if (new.compareTo(old) == 0) return@mapNotNull null
            val sign = if ((c.originalAmount ?: c.amount).signum() < 0) BigDecimal.ONE.negate() else BigDecimal.ONE
            val newAmount = if (c.originalAmount == null || c.amount.abs().compareTo(old) == 0) {
                new
            } else {
                c.amount.abs().multiply(new).divide(old, maxOf(c.amount.scale(), 2), RoundingMode.HALF_UP)
            }
            c.copy(
                amount = newAmount * sign,
                originalAmount = c.originalAmount?.let { new * sign },
                updatedAt = now
            )
        }
    }

    /**
     * When a duplicate transaction is removed, its [moving] contributions go to
     * the surviving transaction ([survivorId], [survivorAmount] in [survivorCurrency])
     * instead of vanishing with it. Shares in another currency can't be a share of
     * the survivor, so those stay in the goal unlinked. Everything linked to the
     * survivor ([survivorLinked] + moved) is then capped to its amount.
     * Returns every row to update.
     */
    fun repoint(
        moving: List<GoalContributionEntity>,
        survivorLinked: List<GoalContributionEntity>,
        survivorId: Long,
        survivorAmount: BigDecimal,
        survivorCurrency: String,
        now: LocalDateTime,
    ): List<GoalContributionEntity> {
        val (sameCurrency, otherCurrency) = moving.partition { (it.originalCurrency ?: survivorCurrency) == survivorCurrency }
        val moved = sameCurrency.map { it.copy(transactionId = survivorId, updatedAt = now) }
        val unlinked = otherCurrency.map { it.copy(transactionId = null, updatedAt = now) }
        val capped = capContributions(survivorLinked + moved, survivorAmount, now).associateBy { it.id }
        return (moved + unlinked).map { capped[it.id] ?: it } + capped.values.filter { c -> moved.none { it.id == c.id } }
    }
}
