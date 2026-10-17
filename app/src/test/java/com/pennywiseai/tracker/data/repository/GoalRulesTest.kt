package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/** #914: Goal progress math, cap scaling, ordering and the free-tier check. */
class GoalRulesTest {

    private val today = LocalDate.of(2026, 10, 16)

    private fun goal(
        id: Long = 1,
        target: String = "1200",
        due: LocalDate = LocalDate.of(2027, 10, 16),
        created: LocalDate = today,
        alreadySaved: String = "0",
        archived: Boolean = false,
    ) = GoalEntity(
        id = id, name = "Goal $id", targetAmount = BigDecimal(target), currency = "INR", dueDate = due,
        alreadySaved = BigDecimal(alreadySaved), isArchived = archived, createdAt = created.atStartOfDay()
    )

    private fun c(amount: String, id: Long = 0, original: String? = null) = GoalContributionEntity(
        id = id, goalId = 1, amount = BigDecimal(amount), originalAmount = original?.let(::BigDecimal)
    )

    private fun assertMoney(expected: String, actual: BigDecimal) =
        assertEquals("expected $expected, got $actual", 0, BigDecimal(expected).compareTo(actual))

    @Test
    fun savedIsAlreadySavedPlusContributionsMinusWithdrawals() {
        val p = GoalRules.progress(goal(alreadySaved = "100"), listOf(c("300"), c("-50")), today)
        assertMoney("350", p.saved)
        assertMoney("850", p.left)
        assertMoney("0", p.over)
        assertEquals(GoalStatus.IN_PROGRESS, p.status)
        assertTrue(p.isActive)
    }

    @Test
    fun savedNeverGoesBelowZero() {
        val p = GoalRules.progress(goal(), listOf(c("100"), c("-500")), today)
        assertMoney("0", p.saved)
        assertMoney("1200", p.left)
    }

    @Test
    fun over100PercentIsReachedWithOver() {
        val p = GoalRules.progress(goal(), listOf(c("1500")), today)
        assertEquals(GoalStatus.REACHED, p.status)
        assertMoney("300", p.over)
        assertMoney("0", p.left)
        assertMoney("0", p.perMonth)
        assertMoney("0", p.behind)
        assertFalse(p.isActive)
    }

    @Test
    fun missedWhenPastDueAndNotReached_archivedWins() {
        val past = goal(due = today.minusDays(1), created = today.minusYears(1))
        val missed = GoalRules.progress(past, emptyList(), today)
        assertEquals(GoalStatus.MISSED, missed.status)
        assertTrue(missed.isActive) // still an Active goal per the glossary
        assertEquals(GoalStatus.ARCHIVED, GoalRules.progress(past.copy(isArchived = true), emptyList(), today).status)
        // Due today is not yet missed.
        assertEquals(GoalStatus.IN_PROGRESS, GoalRules.progress(goal(due = today), emptyList(), today).status)
    }

    @Test
    fun perMonthSplitsLeftOverMonthsRoundedUp() {
        // 12 whole months left.
        assertMoney("100", GoalRules.progress(goal(), emptyList(), today).perMonth)
        // 12 months + 4 days rounds up to 13 → 1200 / 13 = 92.307… → 93 (whole units, rounded up).
        val p = GoalRules.progress(goal(due = LocalDate.of(2027, 10, 20)), emptyList(), today)
        assertEquals(13, p.monthsLeft)
        assertMoney("93", p.perMonth)
    }

    @Test
    fun finalMonthAndPastDueAskForTheFullAmountLeft() {
        val finalMonth = GoalRules.progress(goal(due = today.plusDays(20)), listOf(c("200")), today)
        assertEquals(1, finalMonth.monthsLeft)
        assertMoney("1000", finalMonth.perMonth)

        val pastDue = GoalRules.progress(goal(due = today.minusMonths(2), created = today.minusYears(1)), listOf(c("200")), today)
        assertEquals(1, pastDue.monthsLeft)
        assertMoney("1000", pastDue.perMonth)

        assertEquals(1, GoalRules.monthsLeft(today, today))
        assertEquals(1, GoalRules.monthsLeft(today, today.plusMonths(1)))
        assertEquals(2, GoalRules.monthsLeft(today, today.plusMonths(1).plusDays(1)))
    }

    @Test
    fun behindMeasuresAgainstAnEvenPaceFromCreation() {
        // Created 2026-01-01, due 2027-01-01 (365 days); on 2026-07-02 (182 days) the pace is 1200 × 182/365 = 598.36.
        val g = goal(created = LocalDate.of(2026, 1, 1), due = LocalDate.of(2027, 1, 1))
        val day = LocalDate.of(2026, 7, 2)
        assertMoney("598.36", GoalRules.expectedByNow(g, day))
        assertMoney("398.36", GoalRules.progress(g, listOf(c("200")), day).behind)
        // Already saved counts from day one.
        assertMoney("0", GoalRules.progress(g.copy(alreadySaved = BigDecimal("600")), emptyList(), day).behind)
        // Ahead of pace → not behind.
        assertMoney("0", GoalRules.progress(g, listOf(c("700")), day).behind)
        // Before creation clamps to 0, past due clamps to the target.
        assertMoney("0", GoalRules.expectedByNow(g, LocalDate.of(2025, 12, 1)))
        assertMoney("1200", GoalRules.expectedByNow(g, LocalDate.of(2027, 6, 1)))
        // Due on the creation day: the whole target is expected.
        assertMoney("1200", GoalRules.expectedByNow(goal(due = today), today))
    }

    @Test
    fun orderingIsSoonestDueFirstReachedLastArchivedExcluded() {
        val late = GoalRules.progress(goal(id = 1, due = today.plusMonths(6)), emptyList(), today)
        val soon = GoalRules.progress(goal(id = 2, due = today.plusMonths(1)), emptyList(), today)
        val reachedSoonest = GoalRules.progress(goal(id = 3, due = today.plusDays(1)), listOf(c("1200")), today)
        val missed = GoalRules.progress(goal(id = 4, due = today.minusDays(3), created = today.minusYears(1)), emptyList(), today)
        val archived = GoalRules.progress(goal(id = 5, due = today, archived = true), emptyList(), today)

        val ordered = GoalRules.orderForDisplay(listOf(late, reachedSoonest, archived, soon, missed))
        assertEquals(listOf(4L, 2L, 1L, 3L), ordered.map { it.goal.id })
    }

    @Test
    fun freeTierAllowsOneActiveGoal_proIsUnlimited() {
        assertTrue(GoalRules.canCreateGoal(isPro = false, activeGoalCount = 0))
        assertFalse(GoalRules.canCreateGoal(isPro = false, activeGoalCount = 1))
        assertTrue(GoalRules.canCreateGoal(isPro = true, activeGoalCount = 7))
    }

    @Test
    fun capAmountsScalesProportionallyAndSumsExactlyToTheCap() {
        val capped = GoalRules.capAmounts(listOf(BigDecimal("100.00"), BigDecimal("50.00"), BigDecimal("50.00")), BigDecimal("100"))
        assertEquals(listOf("50.00", "25.00", "25.00"), capped.map { it.toPlainString() })

        // Thirds: largest remainder hands out the spare cent; the sum is exact.
        val thirds = GoalRules.capAmounts(List(3) { BigDecimal("10.00") }, BigDecimal("10.00"))
        assertMoney("10.00", thirds.fold(BigDecimal.ZERO, BigDecimal::add))
        assertTrue(thirds.all { it.signum() >= 0 })
        assertEquals(setOf("3.33", "3.34"), thirds.map { it.toPlainString() }.toSet())

        // Already within the cap: unchanged. Cap of zero: all zero.
        val fits = listOf(BigDecimal("40"), BigDecimal("50"))
        assertEquals(fits, GoalRules.capAmounts(fits, BigDecimal("100")))
        assertTrue(GoalRules.capAmounts(fits, BigDecimal.ZERO).all { it.signum() == 0 })
    }

    @Test
    fun capContributionsScalesSharesAndConvertedAmountsKeepingSigns() {
        val now = LocalDateTime.of(2026, 10, 16, 9, 0)
        val sameCurrency = c("600.00", id = 1, original = "600.00")
        val converted = c("4.00", id = 2, original = "400.00") // e.g. 400 INR → 4.00 in the goal currency
        val changed = GoalRules.capContributions(listOf(sameCurrency, converted), BigDecimal("500"), now)

        assertEquals(2, changed.size)
        val a = changed.first { it.id == 1L }
        val b = changed.first { it.id == 2L }
        assertMoney("300", a.originalAmount!!)
        assertMoney("300", a.amount)
        assertMoney("200", b.originalAmount!!)
        assertMoney("2.00", b.amount)
        assertEquals(now, a.updatedAt)
        assertMoney("500", a.originalAmount!! + b.originalAmount!!)

        // A linked withdrawal stays negative.
        val w = GoalRules.capContributions(listOf(c("-80", id = 3, original = "-80")), BigDecimal("50"), now).single()
        assertMoney("-50", w.amount)
        assertMoney("-50", w.originalAmount!!)

        // Fits already → nothing to update.
        assertTrue(GoalRules.capContributions(listOf(sameCurrency), BigDecimal("600"), now).isEmpty())
    }

    @Test
    fun unallocatedIsWhatTheTransactionHasLeft() {
        val linked = listOf(c("300", id = 1, original = "300"), c("2.00", id = 2, original = "200"))
        assertMoney("500", GoalRules.unallocated(BigDecimal("1000"), linked))
        assertMoney("800", GoalRules.unallocated(BigDecimal("1000"), linked, excludingId = 1))
        assertMoney("0", GoalRules.unallocated(BigDecimal("400"), linked))
    }

    private fun linked(id: Long, share: String, txn: Long, currency: String = "INR", amount: String = share) = GoalContributionEntity(
        id = id, goalId = 1, amount = BigDecimal(amount), originalAmount = BigDecimal(share),
        originalCurrency = currency, transactionId = txn
    )

    @Test
    fun repointMovesSameCurrencySharesToTheSurvivorAndCapsThem() {
        val now = LocalDateTime.of(2026, 10, 16, 12, 0)
        val changed = GoalRules.repoint(
            moving = listOf(linked(1, "300", txn = 8)),
            survivorLinked = listOf(linked(2, "300", txn = 9)),
            survivorId = 9, survivorAmount = BigDecimal("400"), survivorCurrency = "INR", now = now,
        ).associateBy { it.id }
        // Both now point at 9 and their shares sum to the survivor's 400.
        assertEquals(setOf(1L, 2L), changed.keys)
        assertTrue(changed.values.all { it.transactionId == 9L })
        assertMoney("400", changed.values.sumOf { it.originalAmount!! })
    }

    @Test
    fun repointKeepsAShareInAnotherCurrencyInTheGoalButUnlinked() {
        val changed = GoalRules.repoint(
            moving = listOf(linked(1, "50", txn = 8, currency = "USD", amount = "4100")),
            survivorLinked = emptyList(),
            survivorId = 9, survivorAmount = BigDecimal("4000"), survivorCurrency = "INR", now = LocalDateTime.now(),
        ).single()
        assertEquals(null, changed.transactionId)
        assertMoney("4100", changed.amount)
    }

    @Test
    fun repointWithRoomToSpareLeavesAmountsAlone() {
        val changed = GoalRules.repoint(
            moving = listOf(linked(1, "100", txn = 8)),
            survivorLinked = emptyList(),
            survivorId = 9, survivorAmount = BigDecimal("500"), survivorCurrency = "INR", now = LocalDateTime.now(),
        ).single()
        assertEquals(9L, changed.transactionId)
        assertMoney("100", changed.amount)
    }
}
