package com.pennywiseai.tracker.domain.review

import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.presentation.share.merchantsPaid
import com.pennywiseai.tracker.presentation.share.trackedFromBankTexts
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class PeriodReviewTest {
    private var nextId = 1L

    private fun tx(
        date: String,
        amount: String = "100",
        type: TransactionType = TransactionType.EXPENSE,
        currency: String = "JOD",
        category: String = "Food",
        impact: BudgetImpactType? = null,
        excluded: Boolean = false,
    ) = TransactionWithSplits(
        TransactionEntity(
            id = nextId, amount = BigDecimal(amount), merchantName = "Shop", category = category,
            transactionType = type, dateTime = LocalDate.parse(date).atTime(12, 0), currency = currency,
            transactionHash = "h${nextId++}", budgetImpactType = impact, budgetCategory = category,
            excludedFromAnalytics = excluded,
        ),
        emptyList()
    )

    private fun range(kind: ReviewKind, offset: Int, today: String, cycle: Int = 1, week: DayOfWeek = DayOfWeek.SUNDAY) =
        reviewRange(kind, offset, LocalDate.parse(today), cycle, week)

    private fun review(
        range: ReviewRange,
        rows: List<TransactionWithSplits>,
        previous: List<TransactionWithSplits> = emptyList(),
        creditAsExpense: Boolean = false,
        loss: Map<String, BigDecimal> = emptyMap(),
    ) = runBlocking { buildPeriodReview(range, rows, previous, creditAsExpense, emptyMap(), loss) }

    // --- Ranges

    @Test
    fun `a recap's week can be found again after later weeks have ended`() {
        val week = range(ReviewKind.WEEK, -1, "2026-11-04")
        val threeWeeksOn = LocalDate.parse("2026-11-25")
        val offset = weekOffsetOf(week.start, threeWeeksOn, DayOfWeek.SUNDAY)
        assertEquals(-4, offset)
        assertEquals(week.start, reviewRange(ReviewKind.WEEK, offset, threeWeeksOn, 1, DayOfWeek.SUNDAY).start)
        // The week still running hasn't got a review; it falls back to the last full one.
        assertEquals(-1, weekOffsetOf(threeWeeksOn, threeWeeksOn, DayOfWeek.SUNDAY))
    }

    @Test
    fun `week is the last full one, starting on the local first day`() {
        val sunday = range(ReviewKind.WEEK, -1, "2026-11-04")
        assertEquals(LocalDate.parse("2026-10-25"), sunday.start)
        assertEquals(LocalDate.parse("2026-10-31"), sunday.end)
        assertEquals(LocalDate.parse("2026-10-18"), sunday.previousStart)
        assertEquals(LocalDate.parse("2026-10-24"), sunday.previousEnd)

        val monday = range(ReviewKind.WEEK, -1, "2026-11-04", week = DayOfWeek.MONDAY)
        assertEquals(LocalDate.parse("2026-10-26"), monday.start)
        assertEquals(LocalDate.parse("2026-11-01"), monday.end)
    }

    @Test
    fun `month follows the budget cycle, not the calendar`() {
        val cycle = range(ReviewKind.MONTH, -1, "2026-11-04", cycle = 25)
        assertEquals(LocalDate.parse("2026-09-25"), cycle.start)
        assertEquals(LocalDate.parse("2026-10-24"), cycle.end)
        assertEquals(LocalDate.parse("2026-08-25"), cycle.previousStart)
        assertEquals(LocalDate.parse("2026-09-24"), cycle.previousEnd)
    }

    @Test
    fun `the running year compares with the same span last year`() {
        val running = range(ReviewKind.YEAR, 0, "2026-11-04")
        assertFalse(running.isComplete)
        assertEquals(LocalDate.parse("2026-11-04"), running.end)
        assertEquals(LocalDate.parse("2025-11-04"), running.previousEnd)

        val finished = range(ReviewKind.YEAR, -1, "2026-11-04")
        assertTrue(finished.isComplete)
        assertEquals(LocalDate.parse("2025-12-31"), finished.end)
    }

    @Test
    fun `january opens on the year that ended, other months on the running one`() {
        assertEquals(-1, defaultReviewOffset(ReviewKind.YEAR, LocalDate.parse("2027-01-11")))
        assertEquals(0, defaultReviewOffset(ReviewKind.YEAR, LocalDate.parse("2026-12-15")))
        assertEquals(-1, defaultReviewOffset(ReviewKind.MONTH, LocalDate.parse("2027-01-11")))
    }

    // --- Money

    @Test
    fun `currencies are reviewed apart, never summed`() {
        val r = review(
            range(ReviewKind.MONTH, -1, "2026-11-04"),
            listOf(tx("2026-10-02", "500"), tx("2026-10-03", "10", currency = "USD"), tx("2026-10-04", "20")),
        )
        assertEquals(setOf("JOD", "USD"), r.currencies.map { it.currency }.toSet())
        assertEquals(BigDecimal("520"), r.currencies.first { it.currency == "JOD" }.spent)
        assertEquals(BigDecimal("10"), r.currencies.first { it.currency == "USD" }.spent)
        // Ordered by activity, not amount.
        assertEquals("JOD", r.currencies.first().currency)
    }

    @Test
    fun `refunds come off spend and aren't income, exclusions drop out`() {
        val r = review(
            range(ReviewKind.MONTH, -1, "2026-11-04"),
            listOf(
                tx("2026-10-02", "300"),
                tx("2026-10-03", "50", type = TransactionType.INCOME, impact = BudgetImpactType.DEDUCT_SPENT),
                tx("2026-10-04", "999", excluded = true),
                tx("2026-10-05", "1000", type = TransactionType.INCOME),
                tx("2026-10-06", "70", type = TransactionType.TRANSFER),
            ),
        ).currencies.single()
        assertEquals(BigDecimal("250"), r.spent)
        assertEquals(BigDecimal("1000"), r.income)
        // Every counted row: the expense, refund, income and transfer, not the excluded one.
        assertEquals(4, r.transactionCount)
        assertEquals(BigDecimal("250"), r.categories.single().amount)
    }

    @Test
    fun `card spend follows the count-as-expense setting`() {
        val rows = listOf(tx("2026-10-02", "300"), tx("2026-10-03", "40", type = TransactionType.CREDIT))
        val r = range(ReviewKind.MONTH, -1, "2026-11-04")
        assertEquals(BigDecimal("300"), review(r, rows).currencies.single().spent)
        assertEquals(BigDecimal("340"), review(r, rows, creditAsExpense = true).currencies.single().spent)
    }

    @Test
    fun `a forgiven loan counts as spent, even with no rows in that currency`() {
        val r = review(
            range(ReviewKind.MONTH, -1, "2026-11-04"),
            listOf(tx("2026-10-02", "100")),
            loss = mapOf("JOD" to BigDecimal("25"), "USD" to BigDecimal("5")),
        )
        assertEquals(BigDecimal("125"), r.currencies.first { it.currency == "JOD" }.spent)
        assertEquals(BigDecimal("5"), r.currencies.first { it.currency == "USD" }.spent)
    }

    // --- Comparison

    @Test
    fun `comparison needs a previous period the data really reaches back to`() {
        val cycle = range(ReviewKind.MONTH, -1, "2026-11-04")
        val rows = listOf(tx("2026-10-02", "120"))
        val covered = review(cycle, rows, listOf(tx("2026-09-02", "100"))).currencies.single()
        assertEquals(BigDecimal("100"), covered.previousSpent)
        assertEquals(20, percentChange(covered.spent, covered.previousSpent))
        // First row three weeks into September: tracking began mid-period.
        assertNull(review(cycle, rows, listOf(tx("2026-09-22", "100"))).currencies.single().previousSpent)
        assertNull(review(cycle, rows).currencies.single().previousSpent)
    }

    @Test
    fun `a sparse week still compares`() {
        val week = range(ReviewKind.WEEK, -1, "2026-11-04")
        val r = review(week, listOf(tx("2026-10-26", "50")), listOf(tx("2026-10-23", "100"))).currencies.single()
        assertEquals(-50, percentChange(r.spent, r.previousSpent))
    }

    @Test
    fun `a fully refunded period still shows`() {
        val r = review(
            range(ReviewKind.MONTH, -1, "2026-11-04"),
            listOf(
                tx("2026-10-02", "100"),
                tx("2026-10-03", "120", type = TransactionType.INCOME, impact = BudgetImpactType.DEDUCT_SPENT),
            ),
        ).currencies.single()
        assertEquals(BigDecimal.ZERO, r.spent)
        assertTrue(r.hasActivity)
    }

    @Test
    fun `a currency that stopped shows its fall to zero, without counting as activity`() {
        val r = review(
            range(ReviewKind.WEEK, -1, "2026-11-04"),
            listOf(tx("2026-10-26", "50")),
            listOf(tx("2026-10-23", "100", currency = "USD")),
        )
        val usd = r.currencies.first { it.currency == "USD" }
        assertEquals(BigDecimal.ZERO, usd.spent)
        assertEquals(BigDecimal("100"), usd.previousSpent)
        assertEquals(-100, percentChange(usd.spent, usd.previousSpent))
        assertFalse(usd.hasActivity)
        assertTrue(r.currencies.first { it.currency == "JOD" }.hasActivity)
    }

    @Test
    fun `no percent against nothing`() {
        assertNull(percentChange(BigDecimal("10"), null))
        assertNull(percentChange(BigDecimal("10"), BigDecimal.ZERO))
        assertEquals(0, percentChange(BigDecimal("100"), BigDecimal("100")))
    }

    @Test
    fun `a cycle lists what moved most, a year where it went`() {
        val rows = listOf(tx("2026-10-02", "500", category = "Rent"), tx("2026-10-03", "90", category = "Fun"))
        val previous = listOf(tx("2026-09-02", "500", category = "Rent"), tx("2026-09-03", "10", category = "Fun"), tx("2026-09-04", "40", category = "Gym"))
        val cycle = review(range(ReviewKind.MONTH, -1, "2026-11-04"), rows, previous).currencies.single()
        assertEquals(listOf("Fun", "Gym", "Rent"), cycle.categories.map { it.name })
        // A category that stopped still shows, as a drop to zero.
        assertEquals(BigDecimal.ZERO, cycle.categories[1].amount)

        val year = review(
            range(ReviewKind.YEAR, -1, "2026-11-04"),
            listOf(tx("2025-03-02", "500", category = "Rent"), tx("2025-04-03", "90", category = "Fun")),
        ).currencies.single()
        assertEquals(listOf("Rent", "Fun"), year.categories.map { it.name })
    }

    @Test
    fun `a year has every month, net of that month's refunds`() {
        val year = review(
            range(ReviewKind.YEAR, -1, "2026-11-04"),
            listOf(
                tx("2025-03-02", "500"),
                tx("2025-03-20", "100", type = TransactionType.INCOME, impact = BudgetImpactType.DEDUCT_SPENT),
                tx("2025-07-02", "40"),
            ),
        ).currencies.single()
        assertEquals(12, year.months.size)
        assertEquals(BigDecimal("400"), year.months.first { it.month == YearMonth.of(2025, 3) }.spent)
        assertEquals(BigDecimal("40"), year.months.first { it.month == YearMonth.of(2025, 7) }.spent)
        assertEquals(BigDecimal.ZERO, year.months.first { it.month == YearMonth.of(2025, 1) }.spent)
    }

    @Test
    fun `only a year says when its data actually starts`() {
        val full = partialYearSpan(LocalDate.parse("2026-01-05"), LocalDate.parse("2026-12-20"))
        assertNull(full)
        assertEquals(
            YearMonth.of(2026, 3) to YearMonth.of(2026, 11),
            partialYearSpan(LocalDate.parse("2026-03-05"), LocalDate.parse("2026-11-04")),
        )
        assertNull(partialYearSpan(null, null))
    }

    // --- Home banner

    private fun prompt(
        today: String,
        handledYear: String? = null,
        handledMonth: String? = null,
        cycle: Int = 1,
        count: (LocalDate, LocalDate) -> Int = { _, _ -> MIN_ROWS_FOR_REVIEW_PROMPT },
    ) = runBlocking {
        reviewPromptFor(LocalDate.parse(today), cycle, handledYear, handledMonth) { s, e -> count(s, e) }
    }

    @Test
    fun `january offers the year until it's handled, then the month`() {
        val year = prompt("2027-01-05")!!
        assertEquals(ReviewKind.YEAR, year.kind)
        assertEquals("2026", year.key)
        assertEquals(LocalDate.parse("2026-12-31"), year.end)

        assertEquals(ReviewKind.MONTH, prompt("2027-01-05", handledYear = "2026")!!.kind)
        assertNull(prompt("2027-01-05", handledYear = "2026", handledMonth = "2026-12"))
    }

    @Test
    fun `the rest of the year offers the finished cycle, under a key old installs recognise`() {
        val p = prompt("2026-11-04")!!
        assertEquals(ReviewKind.MONTH, p.kind)
        assertEquals("2026-10", p.key)
        assertNull(prompt("2026-11-04", handledMonth = "2026-10"))
        // A non-calendar cycle still keys on the month it ended in.
        assertEquals("2026-10", prompt("2026-11-04", cycle = 25)!!.key)
        assertEquals(LocalDate.parse("2026-09-25"), prompt("2026-11-04", cycle = 25)!!.start)
    }

    @Test
    fun `a thin period earns no banner`() {
        assertNull(prompt("2026-11-04", count = { _, _ -> MIN_ROWS_FOR_REVIEW_PROMPT - 1 }))
        // A thin year in January falls through to the month rather than to nothing.
        val p = prompt("2027-01-05", count = { start, _ -> if (start.monthValue == 12) 30 else 3 })!!
        assertEquals(ReviewKind.MONTH, p.kind)
    }

    // --- Share card counts

    @Test
    fun `the card only claims rows that came from a bank text`() {
        val rows = listOf(
            tx("2026-10-02").transaction.copy(smsSender = "BANK", smsBody = "x"),
            tx("2026-10-03").transaction,
            tx("2026-10-04").transaction.copy(smsBody = "statement block"),
        ) + listOf("STATEMENT", "GPay PDF", "PhonePe PDF", "Paytm PDF", "Slice PDF", "ICICI PDF").map {
            tx("2026-10-05").transaction.copy(smsSender = it, smsBody = "statement block")
        }
        assertEquals(1, trackedFromBankTexts(rows))
    }

    @Test
    fun `merchants are distinct places actually paid`() {
        val rows = listOf(
            tx("2026-10-02").transaction.copy(merchantName = "Cafe"),
            tx("2026-10-03").transaction.copy(merchantName = " cafe "),
            tx("2026-10-04", type = TransactionType.CREDIT).transaction.copy(merchantName = "Books"),
            tx("2026-10-05", type = TransactionType.INCOME).transaction.copy(merchantName = "Employer"),
            tx("2026-10-06", excluded = true).transaction.copy(merchantName = "Hidden"),
        )
        assertEquals(2, merchantsPaid(rows))
    }
}
