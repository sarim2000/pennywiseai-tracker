package com.pennywiseai.tracker.domain.goals

import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.ledger.forTotals
import com.pennywiseai.tracker.data.repository.GoalRules
import com.pennywiseai.tracker.domain.insights.InsightAction
import com.pennywiseai.tracker.domain.insights.InsightFact.GoalsFit
import com.pennywiseai.tracker.domain.insights.InsightRules
import com.pennywiseai.tracker.domain.insights.InsightSeverity
import com.pennywiseai.tracker.domain.review.CycleTotal
import com.pennywiseai.tracker.domain.review.PeriodDetail
import com.pennywiseai.tracker.domain.spending.CycleAverages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class GoalAffordabilityTest {
    private val today = LocalDate.of(2026, 11, 16)
    private var nextId = 1L

    private fun d(v: String) = BigDecimal(v)

    /** A goal 12 months out: [target] − [saved] spread over 12 months. */
    private fun goal(
        target: String,
        currency: String = "INR",
        saved: String = "0",
        due: LocalDate = today.plusMonths(12),
        archived: Boolean = false,
    ) = GoalRules.progress(
        GoalEntity(
            id = nextId++, name = "Goal", targetAmount = d(target), currency = currency, dueDate = due,
            alreadySaved = d(saved), isArchived = archived, createdAt = today.minusMonths(1).atStartOfDay(),
        ),
        emptyList(),
        today,
    )

    private fun usual(currency: String, income: String, spent: String) =
        currency to CycleAverages(currency, d(income), d(spent))

    @Test
    fun `adds what goals in progress need a month, per currency`() {
        val result = GoalAffordability.of(
            listOf(goal("12000"), goal("24000"), goal("1200", currency = "USD")),
            mapOf(usual("INR", "50000", "40000"), usual("USD", "1000", "900")),
        ).associateBy { it.currency }
        val inr = result.getValue("INR")
        assertEquals(d("3000"), inr.needed)
        assertEquals(2, inr.goalCount)
        assertEquals(true, inr.fits)
        assertNull(inr.shortBy)
        assertEquals(d("100"), result.getValue("USD").needed)
    }

    @Test
    fun `reached, missed and archived goals ask nothing more`() {
        val result = GoalAffordability.of(
            listOf(
                goal("12000"),
                goal("5000", saved = "5000"),
                goal("5000", due = today.minusDays(1)),
                goal("5000", archived = true),
            ),
            mapOf(usual("INR", "50000", "40000")),
        ).single()
        assertEquals(d("1000"), result.needed)
        assertEquals(1, result.goalCount)
    }

    @Test
    fun `short when a usual month leaves less than the goals need`() {
        val result = GoalAffordability.of(listOf(goal("120000")), mapOf(usual("INR", "50000", "46000"))).single()
        assertEquals(false, result.fits)
        assertEquals(d("6000"), result.shortBy)
    }

    @Test
    fun `a goal funded by a linked expense each cycle isn't asked for that money twice`() {
        // A 1,000 deposit debit every cycle, put toward the goal, counts as spending too.
        val cycles = (3 downTo 0).map { ago ->
            val start = today.withDayOfMonth(1).minusMonths(ago.toLong())
            CycleTotal(start, start.plusMonths(1).minusDays(1), d("50000"), d("49500"), emptyMap())
        }
        val deposits = cycles.mapIndexed { i, cycle ->
            TransactionWithSplits(
                TransactionEntity(
                    id = 10L + i, amount = d("1000"), merchantName = "Deposit", category = "Savings",
                    currency = "INR", transactionType = TransactionType.EXPENSE,
                    dateTime = cycle.start.plusDays(4).atTime(9, 0), transactionHash = "deposit-$i",
                ),
                emptyList(),
            )
        }
        val linked = deposits.map {
            GoalContributionEntity(
                goalId = 1, amount = d("1000"), originalAmount = d("1000"), originalCurrency = "INR",
                transactionId = it.transaction.id,
            )
        }
        val detail = PeriodDetail(mapOf("INR" to cycles), deposits)

        // The current cycle's deposit isn't part of a usual month.
        assertEquals(mapOf("INR" to d("1000.00")), GoalAffordability.savedThroughSpending(detail, linked))
        val result = GoalAffordabilityLoader.affordability(GoalsToWeigh(listOf(goal("12000")), linked), detail).single()
        assertEquals(d("1500.00"), result.surplus)
        assertEquals(true, result.fits)
        val unlinked = GoalAffordabilityLoader.affordability(GoalsToWeigh(listOf(goal("12000")), emptyList()), detail).single()
        assertEquals(false, unlinked.fits)
    }

    @Test
    fun `only the part of a linked row that counted as spending is given back`() {
        // A 1,000 bill each cycle, 800 of it lent out: only 200 lowered the surplus.
        val cycles = (3 downTo 0).map { ago ->
            val start = today.withDayOfMonth(1).minusMonths(ago.toLong())
            CycleTotal(start, start.plusMonths(1).minusDays(1), d("50000"), d("49500"), emptyMap())
        }
        val bills = cycles.mapIndexed { i, cycle ->
            TransactionWithSplits(
                TransactionEntity(
                    id = 20L + i, amount = d("1000"), merchantName = "Dinner", category = "Food",
                    currency = "INR", transactionType = TransactionType.EXPENSE,
                    dateTime = cycle.start.plusDays(4).atTime(9, 0), transactionHash = "bill-$i",
                    lentSharesTotal = d("800"),
                ),
                emptyList(),
            )
        }
        val linked = bills.map {
            GoalContributionEntity(
                goalId = 1, amount = d("1000"), originalAmount = d("1000"), originalCurrency = "INR",
                transactionId = it.transaction.id,
            )
        }
        // As Period Review hands them over: cut to what counts toward totals.
        val detail = PeriodDetail(mapOf("INR" to cycles), bills.forTotals())

        assertEquals(mapOf("INR" to d("200.00")), GoalAffordability.savedThroughSpending(detail, linked))
        // Raw rows are cut the same way.
        assertEquals(
            mapOf("INR" to d("200.00")),
            GoalAffordability.savedThroughSpending(PeriodDetail(mapOf("INR" to cycles), bills), linked),
        )
    }

    @Test
    fun `no income in the goal's currency is no data, not unaffordable`() {
        val noHistory = GoalAffordability.of(listOf(goal("12000", currency = "EUR")), mapOf(usual("INR", "1", "0"))).single()
        assertNull(noHistory.surplus)
        assertNull(noHistory.fits)
        assertNull(noHistory.shortBy)
        val noIncome = GoalAffordability.of(listOf(goal("12000")), mapOf(usual("INR", "0", "500"))).single()
        assertNull(noIncome.fits)
    }

    @Test
    fun `insight cheers when goals fit, warns when short, and stays quiet with no data`() {
        val fits = GoalAffordability("INR", d("3000"), 2, d("10000"))
        val short = GoalAffordability("USD", d("300"), 1, d("-50"))
        val noData = GoalAffordability("EUR", d("100"), 1, null)
        val insights = InsightRules.goalsFit(listOf(fits, short, noData), "2026-11-01:all")
        assertEquals(listOf("INR", "USD"), insights.map { it.currency })
        assertEquals(listOf(InsightSeverity.GOOD, InsightSeverity.WARNING), insights.map { it.severity })
        assertTrue(insights.all { it.action == InsightAction.OpenGoals })
        assertEquals("goals_fit:v2:2026-11-01:all:USD:all", insights[1].id)
        val fact = insights[1].fact as GoalsFit
        assertEquals("USD", fact.needed.currency)
        assertEquals(d("-50"), fact.surplus.amount)
        assertEquals(d("350"), fact.shortBy?.amount)
        assertNull((insights[0].fact as GoalsFit).shortBy)
    }
}
