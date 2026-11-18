package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.utils.Money
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class SpendingCalendarTest {

    private fun d(s: String) = BigDecimal(s)
    private fun date(s: String) = LocalDate.parse(s)
    private val november = YearMonth.of(2026, 11)
    private var nextId = 1L

    private fun tx(
        on: String,
        amount: String = "100",
        currency: String = "INR",
        type: TransactionType = TransactionType.EXPENSE,
        merchant: String = "Shop",
        impact: BudgetImpactType? = null,
        excluded: Boolean = false,
    ) = TransactionEntity(
        id = nextId, amount = d(amount), merchantName = merchant, category = "Food", transactionType = type,
        dateTime = date(on).atTime(12, 0), currency = currency, transactionHash = "h${nextId++}",
        budgetImpactType = impact, budgetCategory = "Food", excludedFromAnalytics = excluded,
    )

    private fun row(tx: TransactionEntity) = TransactionWithSplits(tx, emptyList())

    private fun bill(name: String, on: String, amount: String = "100", currency: String = "INR") =
        Bill(name, Money(d(amount), currency), date(on))

    private fun days(
        rows: List<TransactionEntity> = emptyList(),
        planned: List<Bill> = emptyList(),
        creditAsExpense: Boolean = false,
        month: YearMonth = november,
        today: LocalDate = date("2026-11-16"),
    ) = runBlocking { SpendingCalendar.days(month, rows.map(::row), planned, creditAsExpense, today) }

    // --- Grid

    @Test
    fun `the grid starts on the local first day of the week`() {
        // 1 Nov 2026 is a Sunday.
        assertEquals(date("2026-11-01"), SpendingCalendar.weeks(november, DayOfWeek.SUNDAY)[0][0])
        val monday = SpendingCalendar.weeks(november, DayOfWeek.MONDAY)
        assertEquals(List(6) { null } + date("2026-11-01"), monday[0])
        val saturday = SpendingCalendar.weeks(november, DayOfWeek.SATURDAY)
        assertEquals(listOf(null, date("2026-11-01")), saturday[0].take(2))
    }

    @Test
    fun `every day of the month appears once, in order, in full weeks`() {
        for (month in listOf(november, YearMonth.of(2026, 2), YearMonth.of(2028, 2), YearMonth.of(2026, 12))) {
            for (first in DayOfWeek.entries) {
                val weeks = SpendingCalendar.weeks(month, first)
                assertTrue(weeks.all { it.size == 7 })
                assertEquals((1..month.lengthOfMonth()).map(month::atDay), weeks.flatten().filterNotNull())
                // Each column is one weekday, so a date sits under its own name.
                weeks.flatten().forEachIndexed { i, day ->
                    if (day != null) assertEquals(first.plus((i % 7).toLong()), day.dayOfWeek)
                }
                assertTrue(weeks.first().any { it != null } && weeks.last().any { it != null })
            }
        }
    }

    @Test
    fun `a February that starts the week fits in exactly four rows, a leap one spills into a fifth`() {
        // 1 Feb 2026 is a Sunday; 1 Feb 2032 is a Sunday too, and 2032 is a leap year.
        assertEquals(4, SpendingCalendar.weeks(YearMonth.of(2026, 2), DayOfWeek.SUNDAY).size)
        val leap = SpendingCalendar.weeks(YearMonth.of(2032, 2), DayOfWeek.SUNDAY)
        assertEquals(5, leap.size)
        assertEquals(listOf(date("2032-02-29")) + List(6) { null }, leap.last())
    }

    // --- Spend

    @Test
    fun `spend is kept per currency, never added across them`() {
        val result = days(listOf(tx("2026-11-03", "500"), tx("2026-11-03", "200"), tx("2026-11-03", "10", "USD")))

        assertEquals(mapOf("INR" to d("700"), "USD" to d("10")), result[date("2026-11-03")]!!.spent)
    }

    @Test
    fun `a refund comes off its day, and a day never goes below zero`() {
        val result = days(
            listOf(
                tx("2026-11-03", "500"),
                tx("2026-11-03", "200", type = TransactionType.INCOME, impact = BudgetImpactType.DEDUCT_SPENT),
                tx("2026-11-04", "50"),
                tx("2026-11-04", "80", type = TransactionType.INCOME, impact = BudgetImpactType.DEDUCT_SPENT),
            )
        )

        assertEquals(d("300"), result[date("2026-11-03")]!!.spent["INR"])
        assertNull(result[date("2026-11-04")])
    }

    @Test
    fun `income, excluded rows and other months aren't spend`() {
        val result = days(
            listOf(
                tx("2026-11-05", "9000", type = TransactionType.INCOME),
                tx("2026-11-06", "300", excluded = true),
                tx("2026-10-31", "100"),
                tx("2026-12-01", "100"),
            )
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `card spend counts only when card purchases count as expense`() {
        val card = listOf(tx("2026-11-07", "400", type = TransactionType.CREDIT))

        assertTrue(days(card, creditAsExpense = false).isEmpty())
        assertEquals(d("400"), days(card, creditAsExpense = true)[date("2026-11-07")]!!.spent["INR"])
    }

    @Test
    fun `a paired transfer and a loan's share aren't spend`() {
        val legs = listOf(tx("2026-11-08", "300", type = TransactionType.TRANSFER), tx("2026-11-08", "300", type = TransactionType.TRANSFER))
            .map { it.copy(transferPairId = "pair") }
        val lent = tx("2026-11-08", "70").copy(loanId = 9)
        val partLent = tx("2026-11-08", "1000", type = TransactionType.CREDIT).copy(loanId = 9, loanContribution = d("400"))

        val result = days(legs + lent + partLent + tx("2026-11-08", "500"), creditAsExpense = true)

        assertEquals(mapOf("INR" to d("1100")), result.getValue(date("2026-11-08")).spent)
    }

    // --- Planned

    @Test
    fun `a payment due is planned, never spend`() {
        val result = days(
            listOf(tx("2026-11-20", "100")),
            planned = listOf(bill("Rent", "2026-11-20", "15000"), bill("Next month", "2026-12-01")),
        )

        val day = result.getValue(date("2026-11-20"))
        assertEquals(mapOf("INR" to d("100")), day.spent)
        assertEquals(listOf("Rent"), day.planned.map { it.name })
        assertNull(result[date("2026-12-01")])
    }

    private fun cardBill(due: String) = Bill("Card", Money(d("2500"), "INR"), date(due), source = BillSource.Card("Test Bank", "4444"))

    @Test
    fun `a card bill still owed from last month shows today, and on its own day in its month`() {
        val overdue = listOf(cardBill("2026-10-25"))

        val thisMonth = days(planned = overdue)
        assertEquals(listOf(date("2026-11-16")), thisMonth.keys.toList())
        assertEquals(date("2026-10-25"), thisMonth.getValue(date("2026-11-16")).planned.single().dueDate)
        assertEquals(listOf(date("2026-10-25")), days(planned = overdue, month = YearMonth.of(2026, 10)).keys.toList())
        assertTrue(days(planned = overdue, month = YearMonth.of(2026, 12)).isEmpty())
    }

    @Test
    fun `a card bill overdue since earlier this month shows today, not on its day`() {
        val result = days(planned = listOf(cardBill("2026-11-01")))

        assertEquals(listOf(date("2026-11-16")), result.keys.toList())
    }

    private fun weekly(next: String) = SubscriptionEntity(
        id = 1, merchantName = "Cleaner", amount = d("100"), nextPaymentDate = date(next), billingCycle = "Weekly",
    )

    private fun upcoming(today: LocalDate, next: String = "2026-11-06") = UpcomingPayments.of(
        subscriptions = listOf(weekly(next)),
        recurring = emptyList(),
        cardBills = emptyList(),
        cards = emptyList(),
        today = today,
        end = november.atEndOfMonth(),
    )

    @Test
    fun `a weekly payment shows on every day it falls`() {
        val today = date("2026-11-01")
        val result = days(planned = upcoming(today), today = today)

        assertEquals(
            listOf("2026-11-06", "2026-11-13", "2026-11-20", "2026-11-27").map(::date),
            result.filterValues { it.planned.isNotEmpty() }.keys.sorted(),
        )
    }

    @Test
    fun `once the payment posts, its day shows the spend and no longer the plan`() {
        val today = date("2026-11-13")
        val paid = tx("2026-11-13", merchant = "Cleaner")
        // Recording the charge moved the subscription on a week.
        val result = days(listOf(paid), planned = upcoming(today, next = "2026-11-20"), today = today)

        val day = result.getValue(today)
        assertTrue(day.planned.isEmpty())
        assertEquals(d("100"), day.spent["INR"])
        // The weeks still to come stay planned.
        assertEquals(1, result.getValue(date("2026-11-20")).planned.size)
    }

    // --- Shading

    @Test
    fun `currencies are ordered by how many days they were spent on`() {
        val result = days(listOf(tx("2026-11-01", currency = "USD"), tx("2026-11-02"), tx("2026-11-03")))

        assertEquals(listOf("INR", "USD"), SpendingCalendar.currencies(result))
    }

    @Test
    fun `shade levels scale to the month's biggest day`() {
        val max = d("1000")
        assertEquals(0, SpendingCalendar.level(null, max))
        assertEquals(0, SpendingCalendar.level(BigDecimal.ZERO, max))
        assertEquals(1, SpendingCalendar.level(d("1"), max))
        assertEquals(1, SpendingCalendar.level(d("250"), max))
        assertEquals(2, SpendingCalendar.level(d("251"), max))
        assertEquals(SpendingCalendar.LEVELS, SpendingCalendar.level(max, max))
    }
}
