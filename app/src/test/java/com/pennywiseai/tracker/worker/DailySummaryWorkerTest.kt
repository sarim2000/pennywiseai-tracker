package com.pennywiseai.tracker.worker

import com.pennywiseai.tracker.data.database.dao.LoanDao
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.LoanStatus
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.preferences.DAILY_SUMMARY_DEFAULT
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.data.repository.LoanRepository
import com.pennywiseai.tracker.domain.insights.Insight
import com.pennywiseai.tracker.domain.insights.InsightFact
import com.pennywiseai.tracker.domain.insights.InsightSeverity
import com.pennywiseai.tracker.domain.insights.InsightsSnapshot
import com.pennywiseai.tracker.domain.review.PeriodReview
import com.pennywiseai.tracker.domain.review.ReviewKind
import com.pennywiseai.tracker.domain.review.reviewRange
import com.pennywiseai.tracker.utils.Money
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** The evening recap's "Spent" is Home's, and it lands at 9 PM local. */
class DailySummaryWorkerTest {

    private fun tx(amount: String, type: TransactionType, currency: String = "JOD", impact: BudgetImpactType? = null) =
        TransactionEntity(
            id = 0, amount = BigDecimal(amount), merchantName = "M", category = "Others",
            transactionType = type, dateTime = LocalDateTime.now(), currency = currency,
            transactionHash = "h$amount$type", budgetImpactType = impact
        )

    @Test
    fun `the recap is off until the user turns it on`() {
        // Owner decision on #968: opt-in from Settings → Notifications.
        assertFalse(DAILY_SUMMARY_DEFAULT)
    }

    @Test
    fun `a loan forgiven today is spent today, even with no other rows`() {
        // Home adds forgiven lent balances back to "Spent" (#938); the recap must too,
        // and must not stay quiet on a day whose only spend was the write-off.
        assertEquals(
            mapOf("JOD" to Money(BigDecimal("10"), "JOD")),
            DailySummaryWorker.daySpent(emptyList(), creditAsExpense = false, settlementLoss = mapOf("JOD" to BigDecimal("10")))
        )
    }

    @Test
    fun `spend nets refunds per currency and adds that currency's loan loss`() {
        val spent = DailySummaryWorker.daySpent(
            listOf(
                tx("30", TransactionType.EXPENSE),
                tx("5", TransactionType.INCOME, impact = BudgetImpactType.DEDUCT_SPENT),
                tx("8", TransactionType.CREDIT),
                tx("100", TransactionType.INCOME),
                tx("12", TransactionType.EXPENSE, currency = "USD")
            ),
            creditAsExpense = true,
            settlementLoss = mapOf("JOD" to BigDecimal("2"))
        )!!
        assertEquals(0, BigDecimal("35").compareTo(spent.getValue("JOD").amount))
        assertEquals(0, BigDecimal("12").compareTo(spent.getValue("USD").amount))
    }

    @Test
    fun `a day with nothing is quiet`() {
        assertNull(DailySummaryWorker.daySpent(emptyList(), creditAsExpense = false, settlementLoss = emptyMap()))
    }

    @Test
    fun `the next run is 9 PM local, across a DST change`() {
        val berlin = ZoneId.of("Europe/Berlin")
        // Saturday before the October switch from CEST (+2) to CET (+1).
        val evening = ZonedDateTime.of(2026, 10, 24, 22, 0, 0, 0, berlin)
        val next = DailySummaryWorker.nextRun(evening)
        assertEquals(ZonedDateTime.of(2026, 10, 25, 21, 0, 0, 0, berlin), next)
        assertEquals(21, next.hour)
        assertEquals(
            ZonedDateTime.of(2026, 10, 24, 21, 0, 0, 0, berlin),
            DailySummaryWorker.nextRun(evening.withHour(9))
        )
    }

    @Test
    fun `settlement loss counts only loans whose source passes the filter`() = runBlocking {
        val loanDao = mockk<LoanDao>(relaxed = true)
        val repo = LoanRepository(loanDao, mockk(relaxed = true), mockk(relaxed = true))
        fun loan(id: Long) = LoanEntity(
            id = id, personName = "Pal", direction = LoanDirection.LENT, originalAmount = BigDecimal("10"),
            remainingAmount = BigDecimal.ZERO, currency = "JOD", status = LoanStatus.SETTLED
        )
        val mine = tx("10", TransactionType.EXPENSE).copy(id = 1, accountNumber = "1111")
        val theirs = tx("10", TransactionType.EXPENSE).copy(id = 2, accountNumber = "2222")
        coEvery { loanDao.getOriginalTransactionForLoan(1) } returns mine
        coEvery { loanDao.getOriginalTransactionForLoan(2) } returns theirs
        coEvery { loanDao.getOriginalTransactionForLoan(3) } returns null
        coEvery { loanDao.getFirstSharedTransactionForLoan(3) } returns null
        coEvery { loanDao.getLinkedTransactions(any()) } returns emptyList()

        val loss = repo.settlementLossByCurrency(listOf(loan(1), loan(2), loan(3))) { it.accountNumber == "1111" }

        assertEquals(mapOf("JOD" to BigDecimal("10")), loss)
    }

    private val today = LocalDate.of(2026, 11, 16)

    private fun spending(
        id: Long,
        name: String,
        remaining: String,
        type: BudgetGroupType = BudgetGroupType.LIMIT,
        limit: String = "500",
        // What the summary shows as the limit; unified mode turns a limit with no rate into 0.
        total: String = limit,
        start: LocalDate = LocalDate.of(2026, 11, 1),
        end: LocalDate = LocalDate.of(2026, 11, 30),
        unconverted: Boolean = false
    ) = BudgetGroupSpending(
        group = BudgetWithCategories(
            BudgetEntity(
                id = id, name = name, limitAmount = BigDecimal(limit), periodType = BudgetPeriodType.MONTHLY,
                startDate = start, endDate = end, groupType = type
            ),
            emptyList()
        ),
        categorySpending = emptyList(),
        totalBudget = BigDecimal(total), totalActual = BigDecimal(total) - BigDecimal(remaining),
        remaining = BigDecimal(remaining), percentageUsed = 0f, dailyAllowance = BigDecimal.ZERO,
        daysRemaining = 14, daysElapsed = 16, windowStart = start, windowEnd = end,
        hasUnconvertedSpend = unconverted
    )

    private fun summary(vararg groups: BudgetGroupSpending) = BudgetOverallSummary(
        groups = groups.toList(), totalIncome = BigDecimal.ZERO, totalLimitBudget = BigDecimal.ZERO,
        totalLimitSpent = BigDecimal.ZERO, totalTargetGoal = BigDecimal.ZERO, totalTargetActual = BigDecimal.ZERO,
        totalExpectedBudget = BigDecimal.ZERO, totalExpectedActual = BigDecimal.ZERO, netSavings = BigDecimal.ZERO,
        savingsRate = 0f, dailyAllowance = BigDecimal.ZERO, daysRemaining = 14, currency = "JOD"
    )

    @Test
    fun `no LIMIT budget, no budget line`() {
        assertTrue(DailySummaryWorker.budgetsLeft(summary(), today).isEmpty())
        assertTrue(
            DailySummaryWorker.budgetsLeft(
                summary(
                    spending(1, "Savings", "100", type = BudgetGroupType.TARGET),
                    spending(2, "Rent", "0", type = BudgetGroupType.EXPECTED)
                ),
                today
            ).isEmpty()
        )
    }

    @Test
    fun `each LIMIT budget running today shows once, tightest first, over budget as a negative`() {
        val left = DailySummaryWorker.budgetsLeft(
            summary(
                spending(1, "Food", "120.5"),
                spending(2, "Fun", "-30"),
                spending(3, "Trip", "90", start = LocalDate.of(2026, 10, 1), end = LocalDate.of(2026, 10, 31)),
                spending(4, "Empty", "0", limit = "0"),
                // Less left than Food in amount, more for its limit.
                spending(5, "Coffee", "40", limit = "50")
            ),
            today
        )
        assertEquals(
            listOf(
                DailySummaryWorker.BudgetLeft("Fun", BigDecimal("-30"), incomplete = false),
                DailySummaryWorker.BudgetLeft("Food", BigDecimal("120.5"), incomplete = false),
                DailySummaryWorker.BudgetLeft("Coffee", BigDecimal("40"), incomplete = false)
            ),
            left
        )
    }

    @Test
    fun `a zero limit with spending is over by all of it, tightest of all`() {
        val left = DailySummaryWorker.budgetsLeft(
            summary(
                spending(1, "Fun", "-30"),
                spending(2, "Nothing", "-500", limit = "0"),
                // A 1000 limit with no rate reads 0 in unified mode: not a zero limit.
                spending(3, "Abroad", "-200", limit = "1000", total = "0")
            ),
            today
        )
        assertEquals(listOf("Nothing", "Fun"), left.map { it.name })
        assertEquals(BigDecimal("-500"), left.first().left)
    }

    @Test
    fun `spending with no rate marks the figure incomplete`() {
        val left = DailySummaryWorker.budgetsLeft(summary(spending(1, "Food", "200", unconverted = true)), today)
        assertTrue(left.single().incomplete)
    }

    @Test
    fun `the incomplete note shows once for the recap, only for budgets it shows`() {
        fun segments(vararg budgets: DailySummaryWorker.BudgetLeft) = DailySummaryWorker.budgetSegments(
            budgets.toList(),
            line = { it.name },
            incompleteNote = "note",
            more = { "$it more" }
        )
        fun budget(name: String, incomplete: Boolean) = DailySummaryWorker.BudgetLeft(name, BigDecimal.TEN, incomplete)

        assertEquals(listOf("A", "B", "note"), segments(budget("A", true), budget("B", true)))
        assertEquals(listOf("A", "B", "note", "1 more"), segments(budget("A", false), budget("B", true), budget("C", false)))
        // A budget only counted in "more" has no figure shown to qualify.
        assertEquals(listOf("A", "B", "1 more"), segments(budget("A", false), budget("B", false), budget("C", true)))
        assertEquals(listOf("A"), segments(budget("A", false)))
    }

    private fun insight(id: String, severity: InsightSeverity) =
        Insight(id, severity, "JOD", InsightFact.BudgetsOnTrack(1), null)

    private fun insights(vararg items: Insight, dismissed: Set<String> = emptySet()) = InsightsSnapshot(
        PeriodReview(reviewRange(ReviewKind.MONTH, 0, today, 1, DayOfWeek.MONDAY), emptyList(), null, null, false),
        items.toList(),
        dismissed,
    )

    @Test
    fun `a tap opens Insights only while an alert there is undismissed`() {
        assertFalse(DailySummaryWorker.opensInsights(null))
        assertFalse(DailySummaryWorker.opensInsights(insights(insight("a", InsightSeverity.WARNING))))
        assertTrue(DailySummaryWorker.opensInsights(insights(insight("a", InsightSeverity.ALERT))))
        assertFalse(
            DailySummaryWorker.opensInsights(insights(insight("a", InsightSeverity.ALERT), dismissed = setOf("a")))
        )
    }
}
