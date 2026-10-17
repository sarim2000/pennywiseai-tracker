package com.pennywiseai.tracker.presentation.goals

import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import com.pennywiseai.tracker.data.repository.GoalRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class GoalFormsTest {

    private val today = LocalDate.of(2026, 10, 16)
    private fun bd(v: String) = BigDecimal(v)

    @Test
    fun `per month helper uses target minus already saved over months left`() {
        val form = GoalForm(name = "Trip", target = "24000", dueDate = today.plusMonths(12), alreadySaved = "0")
        assertEquals(0, bd("2000.00").compareTo(form.perMonth(today, null, emptyList())))
        val withSaved = form.copy(alreadySaved = "4800")
        assertEquals(0, bd("1600.00").compareTo(withSaved.perMonth(today, null, emptyList())))
        // Rounded up to a whole unit, so paying it still reaches the target.
        val uneven = form.copy(target = "1000", dueDate = today.plusMonths(3))
        assertEquals(bd("334"), uneven.perMonth(today, null, emptyList()))
    }

    @Test
    fun `per month helper counts contributions when editing and is null once nothing is left`() {
        val goal = GoalEntity(id = 1, name = "Trip", targetAmount = bd("1200"), dueDate = today.plusMonths(6), createdAt = today.atStartOfDay())
        val paid = listOf(GoalContributionEntity(goalId = 1, amount = bd("600")))
        val form = GoalForm.from(goal)
        assertEquals(0, bd("100.00").compareTo(form.perMonth(today, goal, paid)))
        assertNull(form.copy(target = "600").perMonth(today, goal, paid))
        assertNull(form.copy(target = "").perMonth(today, goal, paid))
    }

    @Test
    fun `new goals need a name, a positive target and a due date after today`() {
        val errors = GoalForm(name = " ", target = "0", dueDate = today, alreadySaved = "x").errors(today, null)
        assertEquals(GoalFieldError.entries.toSet(), errors)
        val ok = GoalForm(name = "Trip", target = "10", dueDate = today.plusDays(1)).errors(today, null)
        assertTrue(ok.isEmpty())
    }

    @Test
    fun `an edited Missed goal may keep its past due date but not move it to another past date`() {
        val goal = GoalEntity(id = 1, name = "Trip", targetAmount = bd("100"), dueDate = today.minusDays(3))
        val form = GoalForm.from(goal)
        assertTrue(form.errors(today, goal).isEmpty())
        assertEquals(setOf(GoalFieldError.DUE_NOT_AFTER_TODAY), form.copy(dueDate = today.minusDays(1)).errors(today, goal))
    }

    @Test
    fun `a withdrawal can't exceed what's saved`() {
        assertNull(validateMovement(bd("50"), isWithdrawal = true, saved = bd("50")))
        assertEquals(MoneyError.MORE_THAN_SAVED, validateMovement(bd("50.01"), isWithdrawal = true, saved = bd("50")))
        assertNull(validateMovement(bd("500"), isWithdrawal = false, saved = BigDecimal.ZERO))
        assertEquals(MoneyError.AMOUNT_INVALID, validateMovement(BigDecimal.ZERO, isWithdrawal = false, saved = bd("1")))
        assertEquals(MoneyError.AMOUNT_INVALID, validateMovement(null, isWithdrawal = false, saved = bd("1")))
    }

    @Test
    fun `editing keeps the sign, caps a withdrawal and drops a stale typed original`() {
        val withdrawal = GoalContributionEntity(id = 3, goalId = 1, amount = bd("-100"), originalAmount = bd("-1.20"), originalCurrency = "USD")
        // Saved is 50 after the withdrawal, so it can grow to 150 at most.
        assertEquals(MoneyError.MORE_THAN_SAVED, (editContribution(withdrawal, bd("151"), today, "", bd("50")).exceptionOrNull() as MoneyException).error)
        val edited = editContribution(withdrawal, bd("150"), today, "rent", bd("50")).getOrThrow()
        assertEquals(bd("-150"), edited.amount)
        assertNull(edited.originalAmount)
        assertNull(edited.originalCurrency)
        val sameAmount = editContribution(withdrawal, bd("100"), today, "", bd("50")).getOrThrow()
        assertEquals(bd("-1.20"), sameAmount.originalAmount)
    }

    @Test
    fun `editing a transaction-linked share scales its original amount`() {
        val linked = GoalContributionEntity(id = 4, goalId = 1, amount = bd("200"), originalAmount = bd("2.40"), originalCurrency = "USD", transactionId = 9)
        val edited = editContribution(linked, bd("100"), today, "", bd("200")).getOrThrow()
        assertEquals(0, bd("1.20").compareTo(edited.originalAmount))
        assertEquals("USD", edited.originalCurrency)
    }

    @Test
    fun `pill shows Behind only once a month or more behind the even pace`() {
        val created = today.minusMonths(6)
        val goal = GoalEntity(id = 1, targetAmount = bd("1200"), dueDate = created.plusMonths(12), createdAt = created.atStartOfDay())
        // Half the time gone, nothing saved: well behind.
        assertTrue(GoalRules.progress(goal, emptyList(), today).pill() is GoalPill.Behind)
        // Saved roughly on pace: on track, shows the monthly amount.
        val onPace = listOf(GoalContributionEntity(goalId = 1, amount = bd("590")))
        assertTrue(GoalRules.progress(goal, onPace, today).pill() is GoalPill.PerMonth)
        val reached = listOf(GoalContributionEntity(goalId = 1, amount = bd("1200")))
        assertEquals(GoalPill.Reached, GoalRules.progress(goal, reached, today).pill())
        val missed = GoalRules.progress(goal.copy(dueDate = today.minusDays(1)), emptyList(), today).pill()
        assertEquals(GoalPill.MissedBy(bd("1200")), missed)
    }

    private fun share(
        share: String?, left: String = "500", txn: String = "INR", goal: String = "INR",
        manual: String? = null, rate: BigDecimal? = bd("80"),
    ) = kotlinx.coroutines.runBlocking {
        shareForGoal(share?.let(::bd), bd(left), txn, goal, manual?.let(::bd)) { amount -> rate?.let { amount * it } }
    }

    private fun Result<GoalShare>.error() = (exceptionOrNull() as? MoneyException)?.error

    @Test
    fun `add to goal takes up to what is left of the transaction`() {
        assertEquals(bd("500"), share("500").getOrThrow().amount) // the prefilled unallocated amount fits
        assertEquals(MoneyError.MORE_THAN_LEFT, share("500.01").error())
        assertEquals(MoneyError.AMOUNT_INVALID, share("0").error())
        assertEquals(MoneyError.AMOUNT_INVALID, share(null).error())
        // Same currency: no original amount is stored.
        assertNull(share("200").getOrThrow().originalAmount)
    }

    @Test
    fun `add to goal converts into the goal currency and keeps the transaction share`() {
        val converted = share("50", txn = "USD", goal = "INR").getOrThrow()
        assertEquals(0, bd("4000").compareTo(converted.amount))
        assertEquals(bd("50"), converted.originalAmount)
        assertEquals("USD", converted.originalCurrency)
    }

    @Test
    fun `with no rate add to goal asks for the amount in the goal currency`() {
        assertEquals(MoneyError.NO_RATE, share("50", txn = "USD", rate = null).error())
        val manual = share("50", txn = "USD", rate = null, manual = "4150").getOrThrow()
        assertEquals(bd("4150"), manual.amount)
        assertEquals(bd("50"), manual.originalAmount)
    }
}
