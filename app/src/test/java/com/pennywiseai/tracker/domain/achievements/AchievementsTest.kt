package com.pennywiseai.tracker.domain.achievements

import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.data.repository.BudgetWindow
import com.pennywiseai.tracker.data.repository.GoalDetail
import com.pennywiseai.tracker.data.repository.GoalRules
import com.pennywiseai.tracker.data.repository.PastWindowSpending
import com.pennywiseai.tracker.domain.review.CurrencyReview
import com.pennywiseai.tracker.domain.review.PeriodReview
import com.pennywiseai.tracker.domain.review.ReviewKind
import com.pennywiseai.tracker.domain.review.ReviewRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class AchievementsTest {

    private val today = LocalDate.parse("2026-11-16")

    private fun day(value: String) = LocalDate.parse(value)

    private fun bd(value: String) = BigDecimal(value)

    // --- Builders: synthetic data only.

    private fun currency(code: String, spent: String, income: String = "0", previous: String? = null) = CurrencyReview(
        currency = code, spent = bd(spent), previousSpent = previous?.let(::bd), income = bd(income),
        transactionCount = 1, categories = emptyList(), months = emptyList(),
    )

    private fun cycle(start: String, vararg currencies: CurrencyReview): PeriodReview {
        val first = day(start)
        val range = ReviewRange(
            ReviewKind.MONTH, first, first.plusMonths(1).minusDays(1),
            first.minusMonths(1), first.minusDays(1), isComplete = true,
        )
        return PeriodReview(range, currencies.toList(), first, first, hasOlder = true)
    }

    private fun goal(
        target: String,
        alreadySaved: String = "0",
        created: String = "2026-01-10",
        currency: String = "JOD",
        vararg moves: Pair<String, String>,
    ): GoalDetail {
        val entity = GoalEntity(
            id = 1, name = "Synthetic goal", targetAmount = bd(target), currency = currency,
            dueDate = day("2027-12-31"), alreadySaved = bd(alreadySaved), createdAt = day(created).atStartOfDay(),
        )
        val contributions = moves.mapIndexed { i, (date, amount) ->
            GoalContributionEntity(id = i + 1L, goalId = 1, amount = bd(amount), date = day(date))
        }
        return GoalDetail(GoalRules.progress(entity, contributions, today), contributions)
    }

    /** One record a day from [start], [count] of them. */
    private fun daily(start: String, count: Int): List<LocalDate> = (0 until count).map { day(start).plusDays(it.toLong()) }

    // --- Tiers and display

    @Test
    fun `progress and next threshold follow the tier earned`() {
        val locked = Achievement(AchievementId.TRACKED, tier = 0, progress = 40, unlockedOn = null)
        assertFalse(locked.earned)
        assertEquals(100, locked.nextThreshold)
        assertEquals(0.4f, locked.fractionToNext, 0.001f)

        val maxed = Achievement(AchievementId.TRACKED, tier = 4, progress = 6_000, unlockedOn = today)
        assertNull(maxed.nextThreshold)
        assertEquals(5_000, maxed.threshold)
        assertEquals(1f, maxed.fractionToNext, 0.001f)
    }

    @Test
    fun `earned come first, newest first, then the closest to earning`() {
        val list = listOf(
            Achievement(AchievementId.STREAK, 0, 1, null),
            Achievement(AchievementId.TRACKED, 1, 120, day("2026-03-01")),
            Achievement(AchievementId.SAVINGS_RATE, 0, 18, null),
            Achievement(AchievementId.GOALS_REACHED, 1, 1, day("2026-09-01")),
        ).forDisplay()
        assertEquals(
            listOf(AchievementId.GOALS_REACHED, AchievementId.TRACKED, AchievementId.SAVINGS_RATE, AchievementId.STREAK),
            list.map { it.id },
        )
    }

    @Test
    fun `contract keys are unique and fit the share contract`() {
        val keys = AchievementId.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.all { it.length <= 40 })
        assertEquals(AchievementId.SAVINGS_UP, AchievementId.fromKey("savings_rate_up"))
        assertNull(AchievementId.fromKey("nope"))
    }

    // --- Transactions tracked

    @Test
    fun `transactions tracked unlocks on the day of the record that crossed each tier`() {
        val a = trackedAchievement(daily("2026-01-01", 520).shuffled(java.util.Random(7)))
        assertEquals(2, a.tier)
        // The 500th record, one a day from 1 January.
        assertEquals(day("2026-01-01").plusDays(499), a.unlockedOn)
        assertEquals(520, a.progress)
        assertEquals(1_000, a.nextThreshold)
    }

    @Test
    fun `fewer than a hundred transactions is locked`() {
        val a = trackedAchievement(daily("2026-01-01", 99))
        assertEquals(0, a.tier)
        assertNull(a.unlockedOn)
        assertEquals(99, a.progress)
    }

    // --- Months in a row

    @Test
    fun `a streak counts calendar months in a row and unlocks on the month's first record`() {
        val recorded = listOf("2026-01-20", "2026-02-03", "2026-02-01", "2026-03-15", "2026-04-02").map(::day)
        val a = streakAchievement(recorded, day("2026-04-30"))
        assertEquals(1, a.tier)
        // Three months in a row reached on March's first record.
        assertEquals(day("2026-03-15"), a.unlockedOn)
        assertEquals(4, a.progress)
    }

    @Test
    fun `a gap restarts the streak but keeps tiers earned before it`() {
        val recorded = listOf("2025-01-05", "2025-02-05", "2025-03-05", "2025-05-05", "2025-06-05").map(::day)
        val a = streakAchievement(recorded, day("2025-06-20"))
        assertEquals(1, a.tier)
        assertEquals(day("2025-03-05"), a.unlockedOn)
        assertEquals(2, a.progress)
    }

    @Test
    fun `a month just started doesn't break the current streak, one missed does`() {
        val recorded = listOf("2026-08-10", "2026-09-10", "2026-10-10").map(::day)
        assertEquals(3, streakAchievement(recorded, day("2026-11-02")).progress)
        assertEquals(0, streakAchievement(recorded, day("2026-12-02")).progress)
    }

    // --- Subscriptions

    @Test
    fun `subscriptions count up to their tiers`() {
        val a = subscriptionsAchievement(listOf("2026-05-01", "2026-02-01", "2026-03-01", "2026-04-01", "2026-06-01").map(::day))
        assertEquals(2, a.tier)
        assertEquals(day("2026-06-01"), a.unlockedOn)
        assertEquals(10, a.nextThreshold)
        assertEquals(0, subscriptionsAchievement(emptyList()).tier)
    }

    // --- Goals

    @Test
    fun `halfway unlocks on the contribution that reached half the target`() {
        val g = goal("1000", "100", "2026-01-10", "JOD", "2026-03-01" to "300", "2026-02-01" to "50", "2026-04-01" to "100")
        // 100 + 50 (Feb) + 300 (Mar) = 450, then 550 in April.
        val a = goalHalfwayAchievement(listOf(g))
        assertTrue(a.earned)
        assertEquals(day("2026-04-01"), a.unlockedOn)
    }

    @Test
    fun `a goal started at half or more is halfway on the day it was made`() {
        val a = goalHalfwayAchievement(listOf(goal("1000", alreadySaved = "600", created = "2026-05-05")))
        assertEquals(day("2026-05-05"), a.unlockedOn)
    }

    @Test
    fun `halfway stays earned after a withdrawal, and shows the best goal's percent while locked`() {
        val dipped = goal("1000", "0", "2026-01-10", "JOD", "2026-02-01" to "600", "2026-03-01" to "-400")
        assertTrue(goalHalfwayAchievement(listOf(dipped)).earned)

        val locked = goalHalfwayAchievement(listOf(goal("1000", "200"), goal("500", "150", currency = "USD")))
        assertFalse(locked.earned)
        assertEquals(30, locked.progress)
    }

    @Test
    fun `goals reached counts goals in any currency, never their amounts`() {
        val goals = listOf(
            goal("100", "0", "2026-01-01", "JOD", "2026-06-01" to "100"),
            goal("5000", "0", "2026-01-01", "INR", "2026-03-01" to "6000"),
            goal("900", "100"),
        )
        val a = goalsReachedAchievement(goals)
        assertEquals(1, a.tier)
        assertEquals(2, a.progress)
        assertEquals(day("2026-03-01"), a.unlockedOn)
        assertEquals(0, goalsReachedAchievement(emptyList()).tier)
    }

    @Test
    fun `a goal without a target reaches nothing`() {
        assertNull(dateReaching(GoalEntity(targetAmount = BigDecimal.ZERO), emptyList(), 100))
    }

    // --- Budgets

    @Test
    fun `budget months in a row under every limit, ending on the month that completed the run`() {
        val months = listOf(
            BudgetMonth(day("2026-05-31"), kept = true),
            BudgetMonth(day("2026-06-30"), kept = false),
            BudgetMonth(day("2026-09-30"), kept = true),
            BudgetMonth(day("2026-07-31"), kept = true),
            BudgetMonth(day("2026-08-31"), kept = true),
            BudgetMonth(day("2026-10-31"), kept = null),
        )
        val a = budgetKeptAchievement(months)
        assertEquals(2, a.tier)
        assertEquals(day("2026-09-30"), a.unlockedOn)
        // October had no limits, so the current run is over.
        assertEquals(0, a.progress)
    }

    @Test
    fun `one month under every limit is the first tier`() {
        val a = budgetKeptAchievement(listOf(BudgetMonth(day("2026-10-31"), kept = true)))
        assertEquals(1, a.tier)
        assertEquals(1, a.progress)
        assertEquals(3, a.nextThreshold)
        assertEquals(0, budgetKeptAchievement(listOf(BudgetMonth(day("2026-10-31"), kept = false))).tier)
    }

    private val october = BudgetWindow(day("2026-10-01"), day("2026-10-31"), 31)

    private fun limit(
        budget: String,
        actual: String,
        created: String = "2026-01-01",
        weeks: List<String> = emptyList(),
    ) = BudgetGroupSpending(
        group = BudgetWithCategories(
            BudgetEntity(
                name = "Budget", limitAmount = bd(budget), periodType = BudgetPeriodType.MONTHLY,
                startDate = october.start, endDate = october.end, createdAt = day(created).atStartOfDay(),
            ),
            emptyList(),
        ),
        categorySpending = emptyList(), totalBudget = bd(budget), totalActual = bd(actual), remaining = BigDecimal.ZERO,
        percentageUsed = 0f, dailyAllowance = BigDecimal.ZERO, daysRemaining = 0, daysElapsed = 0,
        previousWindows = weeks.map { PastWindowSpending(october, bd(it)) },
    )

    private fun october(vararg groups: BudgetGroupSpending) = BudgetOverallSummary(
        groups = groups.toList(), totalIncome = BigDecimal.ZERO, totalLimitBudget = BigDecimal.ZERO,
        totalLimitSpent = BigDecimal.ZERO, totalTargetGoal = BigDecimal.ZERO, totalTargetActual = BigDecimal.ZERO,
        totalExpectedBudget = BigDecimal.ZERO, totalExpectedActual = BigDecimal.ZERO, netSavings = BigDecimal.ZERO,
        savingsRate = 0f, dailyAllowance = BigDecimal.ZERO, daysRemaining = 0, currency = "JOD", pageWindow = october,
    )

    private val trackedInOctober = listOf(day("2026-10-12"))

    @Test
    fun `a month is kept when every limit in place ended under its amount`() {
        val month = budgetMonthOf(october(limit("100", "90"), limit("50", "50")), october.end, trackedInOctober)
        assertEquals(BudgetMonth(october.end, kept = true), month)
        assertEquals(false, budgetMonthOf(october(limit("100", "90"), limit("50", "51")), october.end, trackedInOctober).kept)
    }

    @Test
    fun `a limit set up after the month began doesn't judge it`() {
        // Made in November: October wasn't kept under it, so the month doesn't count.
        assertNull(budgetMonthOf(october(limit("100", "10", created = "2026-11-02")), october.end, trackedInOctober).kept)
        // Beside an older limit, only the older one judges October.
        val mixed = october(limit("100", "90"), limit("50", "80", created = "2026-10-20"))
        assertEquals(true, budgetMonthOf(mixed, october.end, trackedInOctober).kept)
    }

    @Test
    fun `a weekly limit is kept only when every week of the month was`() {
        // The last week, the one the summary shows, was under; an earlier one wasn't.
        val weekly = october(limit("100", "60", weeks = listOf("90", "130", "70")))
        assertEquals(false, budgetMonthOf(weekly, october.end, trackedInOctober).kept)
        val kept = october(limit("100", "60", weeks = listOf("90", "100", "70")))
        assertEquals(true, budgetMonthOf(kept, october.end, trackedInOctober).kept)
    }

    @Test
    fun `a month with nothing tracked, or no limits, isn't a month kept`() {
        assertNull(budgetMonthOf(october(limit("100", "0")), october.end, listOf(day("2026-09-30"))).kept)
        assertNull(budgetMonthOf(october(), october.end, trackedInOctober).kept)
    }

    // --- Savings rate

    @Test
    fun `savings rate is income-based, per currency, and unlocks on the first cycle that met each tier`() {
        val cycles = listOf(
            cycle("2026-07-01", currency("JOD", spent = "850", income = "1000")),
            // 25% in USD while JOD sat at 10%: USD earns it on its own.
            cycle("2026-08-01", currency("JOD", spent = "900", income = "1000"), currency("USD", spent = "75", income = "100")),
            cycle("2026-09-01", currency("JOD", spent = "650", income = "1000")),
        )
        val a = savingsRateAchievement(cycles)
        assertEquals(2, a.tier)
        // 20% first met in August (USD), 30% in September (JOD).
        assertEquals(day("2026-08-31"), savingsRateAchievement(cycles.take(2)).unlockedOn)
        assertEquals(day("2026-09-30"), a.unlockedOn)
        assertEquals(35, a.progress)
    }

    @Test
    fun `currencies are never added together for the rate`() {
        // Each currency kept 10%, so neither reached 20%, whatever their figures add up to.
        val a = savingsRateAchievement(
            listOf(cycle("2026-09-01", currency("JOD", spent = "900", income = "1000"), currency("USD", spent = "90", income = "100")))
        )
        assertFalse(a.earned)
        assertEquals(10, a.progress)
    }

    @Test
    fun `no income means no savings rate`() {
        val a = savingsRateAchievement(listOf(cycle("2026-09-01", currency("JOD", spent = "100"))))
        assertFalse(a.earned)
        assertEquals(0, a.progress)
    }

    @Test
    fun `savings up compares a currency with itself in the cycle just before`() {
        val cycles = listOf(
            cycle("2026-07-01", currency("JOD", spent = "900", income = "1000")),
            cycle("2026-08-01", currency("JOD", spent = "880", income = "1000")),
        )
        val a = savingsUpAchievement(cycles)
        assertTrue(a.earned)
        assertEquals(day("2026-08-31"), a.unlockedOn)
        assertEquals(2, a.progress)
    }

    @Test
    fun `savings up ignores other currencies and cycles with a gap between them`() {
        val otherCurrency = listOf(
            cycle("2026-07-01", currency("USD", spent = "90", income = "100")),
            cycle("2026-08-01", currency("JOD", spent = "100", income = "1000")),
        )
        assertFalse(savingsUpAchievement(otherCurrency).earned)

        val gap = listOf(
            cycle("2026-05-01", currency("JOD", spent = "900", income = "1000")),
            cycle("2026-08-01", currency("JOD", spent = "100", income = "1000")),
        )
        assertFalse(savingsUpAchievement(gap).earned)
    }

    @Test
    fun `a falling savings rate earns nothing`() {
        val cycles = listOf(
            cycle("2026-07-01", currency("JOD", spent = "500", income = "1000")),
            cycle("2026-08-01", currency("JOD", spent = "700", income = "1000")),
        )
        val a = savingsUpAchievement(cycles)
        assertFalse(a.earned)
        assertEquals(0, a.progress)
    }

    @Test
    fun `a currency nothing was spent in isn't one saved in`() {
        // A stray USD refund-as-income with no USD spending isn't 100% kept.
        val cycles = listOf(cycle("2026-09-01", currency("JOD", spent = "900", income = "1000"), currency("USD", spent = "0", income = "40")))
        val a = savingsRateAchievement(cycles)
        assertFalse(a.earned)
        assertEquals(10, a.progress)
    }

    @Test
    fun `the cycle tracking began late in doesn't count for savings`() {
        // Tracking began on 20 August: August holds its salary and ten days of spending.
        val cycles = listOf(
            cycle("2026-08-01", currency("JOD", spent = "200", income = "1000")),
            cycle("2026-09-01", currency("JOD", spent = "900", income = "1000")),
        )
        val inputs = AchievementInputs(daily("2026-08-20", 60), cycles, emptyList(), emptyList(), emptyList())
        val all = achievementsOf(inputs, today).associateBy { it.id }
        assertFalse(all.getValue(AchievementId.SAVINGS_RATE).earned)
        assertEquals(10, all.getValue(AchievementId.SAVINGS_RATE).progress)

        // Begun within August's first quarter, August counts.
        assertEquals(listOf(cycles[0].range.start, cycles[1].range.start), trackedCycles(cycles, day("2026-08-07")).map { it.range.start })
        assertTrue(trackedCycles(cycles, null).isEmpty())
    }

    // --- Spending down

    @Test
    fun `spending down needs ten percent less than a comparable cycle before`() {
        val a = spendingDownAchievement(
            listOf(
                cycle("2026-08-01", currency("JOD", spent = "950", previous = "1000")),
                cycle("2026-09-01", currency("JOD", spent = "855", previous = "950")),
            )
        )
        assertTrue(a.earned)
        assertEquals(day("2026-09-30"), a.unlockedOn)
        assertEquals(7, spendingDownAchievement(listOf(cycle("2026-09-01", currency("JOD", spent = "930", previous = "1000")))).progress)
    }

    @Test
    fun `no comparison, or a currency left unused, isn't spending less`() {
        val a = spendingDownAchievement(
            listOf(
                cycle("2026-08-01", currency("JOD", spent = "100", previous = null)),
                cycle("2026-09-01", currency("USD", spent = "0", previous = "500")),
            )
        )
        assertFalse(a.earned)
        assertEquals(0, a.progress)
    }

    @Test
    fun `spending down is per currency`() {
        // JOD rose, USD fell by 20%: USD earns it without JOD's figures.
        val a = spendingDownAchievement(
            listOf(cycle("2026-09-01", currency("JOD", spent = "1200", previous = "1000"), currency("USD", spent = "80", previous = "100")))
        )
        assertTrue(a.earned)
        assertEquals(20, a.progress)
    }

    // --- All of them

    @Test
    fun `every achievement is worked out, in order, and an empty history earns none`() {
        val all = achievementsOf(AchievementInputs(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()), today)
        assertEquals(AchievementId.entries.toList(), all.map { it.id })
        assertTrue(all.none { it.earned })
    }
}
