package com.pennywiseai.tracker.presentation.goals

import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.data.repository.GoalRules
import com.pennywiseai.tracker.domain.goals.monthlyDisplay
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Inline validation problems; the screen maps each to a string. */
enum class GoalFieldError { NAME_BLANK, TARGET_INVALID, DUE_NOT_AFTER_TODAY, ALREADY_SAVED_INVALID }

/**
 * The create/edit goal form as typed. Pure, so the live "per month" helper and
 * validation are unit-testable without a ViewModel.
 */
data class GoalForm(
    val name: String = "",
    val target: String = "",
    val currency: String = "INR",
    val dueDate: LocalDate? = null,
    val color: String = "#1565C0",
    val alreadySaved: String = "",
) {
    val targetAmount: BigDecimal? get() = target.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
    val alreadySavedAmount: BigDecimal? get() =
        if (alreadySaved.isBlank()) BigDecimal.ZERO else alreadySaved.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }

    /**
     * Field errors. [original] is the stored goal when editing: its due date may
     * stay in the past (a Missed goal), only a *changed* date must be after today.
     */
    fun errors(today: LocalDate, original: GoalEntity?): Set<GoalFieldError> = buildSet {
        if (name.isBlank()) add(GoalFieldError.NAME_BLANK)
        if (targetAmount == null) add(GoalFieldError.TARGET_INVALID)
        val due = dueDate
        if (due == null || (!due.isAfter(today) && due != original?.dueDate)) add(GoalFieldError.DUE_NOT_AFTER_TODAY)
        if (original == null && alreadySavedAmount == null) add(GoalFieldError.ALREADY_SAVED_INVALID)
    }

    /**
     * "Save X a month to make it", from [GoalRules] so it matches the card.
     * Null until target and due date are valid, or when nothing is left to save.
     */
    fun perMonth(today: LocalDate, original: GoalEntity?, contributions: List<GoalContributionEntity>): BigDecimal? {
        val target = targetAmount ?: return null
        val due = dueDate ?: return null
        val draft = (original ?: GoalEntity(createdAt = today.atStartOfDay())).copy(
            targetAmount = target,
            dueDate = due,
            currency = currency,
            alreadySaved = if (original == null) alreadySavedAmount ?: return null else original.alreadySaved,
            isArchived = false,
        )
        val progress = GoalRules.progress(draft, contributions, today)
        return progress.monthlyDisplay().takeIf { progress.left.signum() > 0 }
    }

    companion object {
        fun from(goal: GoalEntity) = GoalForm(
            name = goal.name,
            target = goal.targetAmount.stripTrailingZeros().toPlainString(),
            currency = goal.currency,
            dueDate = goal.dueDate,
            color = goal.color,
        )
    }
}

/**
 * The ring's centre label while its sweep animates toward [target] (a [ratioOf]):
 * read off the same [animated] ratio, and exactly [percent] once it lands (or, heading
 * past 100%, once the ring is full, so it doesn't tick on), so the float never shows 28%
 * for 29%.
 */
fun ringPercent(animated: Float, target: Float, percent: Int): Int =
    if (animated == target || (animated >= 1f && target >= 1f)) percent else kotlin.math.floor(animated * 100f + 0.001f).toInt()

/** Why an Add money / Take out / contribution edit didn't save. */
enum class MoneyError { AMOUNT_INVALID, MORE_THAN_SAVED, MORE_THAN_LEFT, NO_RATE, FAILED }

/**
 * Checks an Add money / Take out amount, already in the goal's currency.
 * A withdrawal can't take out more than is saved.
 */
fun validateMovement(amount: BigDecimal?, isWithdrawal: Boolean, saved: BigDecimal): MoneyError? = when {
    amount == null || amount.signum() <= 0 -> MoneyError.AMOUNT_INVALID
    isWithdrawal && amount > saved -> MoneyError.MORE_THAN_SAVED
    else -> null
}

/**
 * Applies an edit to a stored contribution. [newAbs] is the positive amount in
 * the goal's currency; the stored sign is kept so a withdrawal stays one. A
 * withdrawal can grow by at most what's saved. When the amount changes, a typed
 * foreign original is dropped (the user now states the goal-currency amount),
 * while a transaction share is scaled by the same ratio so it still fits.
 */
fun editContribution(
    existing: GoalContributionEntity,
    newAbs: BigDecimal?,
    date: LocalDate,
    note: String,
    saved: BigDecimal,
): Result<GoalContributionEntity> {
    val isWithdrawal = existing.amount.signum() < 0
    val oldAbs = existing.amount.abs()
    validateMovement(newAbs, isWithdrawal, saved + oldAbs)?.let { return Result.failure(MoneyException(it)) }
    newAbs!!
    val signed = if (isWithdrawal) newAbs.negate() else newAbs
    val changed = newAbs.compareTo(oldAbs) != 0
    val (origAmount, origCurrency) = when {
        !changed || existing.originalAmount == null -> existing.originalAmount to existing.originalCurrency
        existing.transactionId == null -> null to null
        else -> existing.originalAmount.multiply(newAbs).divide(oldAbs, 2, RoundingMode.HALF_UP) to existing.originalCurrency
    }
    return Result.success(
        existing.copy(amount = signed, date = date, note = note, originalAmount = origAmount, originalCurrency = origCurrency)
    )
}

class MoneyException(val error: MoneyError) : IllegalArgumentException(error.name)

/** A transaction share to put toward a goal: [amount] in the goal's currency, plus the share itself when converted. */
data class GoalShare(val amount: BigDecimal, val originalAmount: BigDecimal?, val originalCurrency: String?)

/**
 * Add to goal: [share] is typed in the transaction's currency and must fit in
 * what's [left] of it. Another goal currency is converted with [convert]; with
 * no rate the user's [manual] amount (in the goal's currency) is used, and
 * without that it fails with [MoneyError.NO_RATE] so the sheet can ask.
 */
suspend fun shareForGoal(
    share: BigDecimal?,
    left: BigDecimal,
    transactionCurrency: String,
    goalCurrency: String,
    manual: BigDecimal?,
    convert: suspend (BigDecimal) -> BigDecimal?,
): Result<GoalShare> {
    if (share == null || share.signum() <= 0) return Result.failure(MoneyException(MoneyError.AMOUNT_INVALID))
    if (share > left) return Result.failure(MoneyException(MoneyError.MORE_THAN_LEFT))
    // Exact match, as GoalRepository compares: a share is only stored when the currencies differ.
    if (transactionCurrency == goalCurrency) return Result.success(GoalShare(share, null, null))
    val converted = manual ?: convert(share) ?: return Result.failure(MoneyException(MoneyError.NO_RATE))
    if (converted.signum() <= 0) return Result.failure(MoneyException(MoneyError.AMOUNT_INVALID))
    return Result.success(GoalShare(converted, share, transactionCurrency))
}
