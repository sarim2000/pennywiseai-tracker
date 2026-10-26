package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.utils.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class SpendOutlookTest {

    private val start = LocalDate.of(2026, 10, 1)
    private val end = LocalDate.of(2026, 10, 31)
    private fun d(s: String) = BigDecimal(s)

    @Test
    fun `no forecast before day 7`() {
        assertNull(SpendForecast.of(d("6000"), "INR", start, end, LocalDate.of(2026, 10, 6), emptyList()))
    }

    @Test
    fun `forecast from day 7 is pace times period length`() {
        val f = SpendForecast.of(d("7000"), "INR", start, end, LocalDate.of(2026, 10, 7), emptyList())!!
        assertEquals(d("31000"), f.projected)
        assertEquals(24, f.daysLeft)
        assertEquals("INR", f.currency)
    }

    @Test
    fun `no usual without three periods of history`() {
        val today = LocalDate.of(2026, 10, 22)
        assertNull(SpendForecast.of(d("22000"), "INR", start, end, today, emptyList())!!.usual)
        assertNull(SpendForecast.of(d("22000"), "INR", start, end, today, listOf(d("1"), d("2")))!!.usual)
        // A period with no data at all doesn't count as history.
        assertNull(SpendForecast.of(d("22000"), "INR", start, end, today, listOf(d("1"), null, d("2")))!!.usual)
    }

    @Test
    fun `over usual gives the gap and the daily spend that lands on usual`() {
        val f = SpendForecast.of(
            d("22000"), "INR", start, end, LocalDate.of(2026, 10, 22),
            listOf(d("27000"), d("30000"), d("33000")),
        )!!
        assertEquals(d("31000"), f.projected)
        assertEquals(d("30000"), f.usual)
        assertEquals(d("1000"), f.overUsual)
        // (30000 - 22000) / 9 days left = 888.9, rounded down.
        assertEquals(d("888"), f.dailyToStayAtUsual)
    }

    @Test
    fun `under usual has no gap and no daily target`() {
        val f = SpendForecast.of(
            d("10000"), "INR", start, end, LocalDate.of(2026, 10, 10),
            listOf(d("40000"), d("40000"), d("40000")),
        )!!
        assertNull(f.overUsual)
        assertNull(f.dailyToStayAtUsual)
    }

    @Test
    fun `already past usual has a gap but no daily target`() {
        val f = SpendForecast.of(
            d("35000"), "INR", start, end, LocalDate.of(2026, 10, 20),
            listOf(d("30000"), d("30000"), d("30000")),
        )!!
        assertEquals(d("24250"), f.overUsual)
        assertNull(f.dailyToStayAtUsual)
    }

    private val today = LocalDate.of(2026, 10, 23)

    @Test
    fun `safe to spend is balance minus bills due in the window`() {
        val bills = listOf(
            Bill("Netflix", Money(d("649"), "INR"), LocalDate.of(2026, 10, 28)),
            Bill("Spotify", Money(d("119"), "INR"), LocalDate.of(2026, 11, 5)), // after the period
            Bill("Old", Money(d("500"), "INR"), LocalDate.of(2026, 10, 20)), // before today
        )
        val r = SafeToSpend.calculate(listOf(Money(d("10000"), "INR")), bills, today, end).single()
        assertEquals(d("649"), r.bills)
        assertEquals(d("9351"), r.amount)
        assertEquals(9, r.days)
        assertEquals(d("1039"), r.perDay)
    }

    @Test
    fun `mixed currencies are never netted together`() {
        val r = SafeToSpend.calculate(
            balances = listOf(Money(d("10000"), "INR"), Money(d("400"), "USD"), Money(d("500"), "INR")),
            bills = listOf(
                Bill("Netflix", Money(d("649"), "INR"), today),
                Bill("iCloud", Money(d("3"), "USD"), end),
                Bill("Gym", Money(d("20"), "EUR"), end),
            ),
            today = today,
            periodEnd = end,
        ).associateBy { it.currency }
        assertEquals(d("9851"), r.getValue("INR").amount)
        assertEquals(d("397"), r.getValue("USD").amount)
        // A bill in a currency with no account still shows, as money short.
        assertEquals(d("-20"), r.getValue("EUR").amount)
    }

    @Test
    fun `bills above the balance give a negative amount and no daily figure`() {
        val r = SafeToSpend.calculate(
            listOf(Money(d("500"), "INR")),
            listOf(Bill("Rent", Money(d("1500"), "INR"), today)),
            today, end,
        ).single()
        assertEquals(d("-1000"), r.amount)
        assertNull(r.perDay)
    }

    @Test
    fun `last day of the period counts as one day`() {
        val r = SafeToSpend.calculate(listOf(Money(d("300"), "INR")), emptyList(), end, end).single()
        assertEquals(1, r.days)
        assertEquals(d("300"), r.perDay)
    }

    private fun capped(budgetLeft: Money?) = SafeToSpend.calculate(
        listOf(Money(d("24000"), "INR")),
        listOf(Bill("Netflix", Money(d("649"), "INR"), today)),
        today, end, budgetLeft,
    ).single()

    @Test
    fun `budget left caps safe to spend and its daily figure`() {
        val r = capped(Money(d("5000"), "INR"))
        assertTrue(r.isCapped)
        assertEquals(d("5000"), r.amount)
        // 5000 / 9 days = 555.6, rounded down.
        assertEquals(d("555"), r.perDay)
    }

    @Test
    fun `no budget means no cap`() {
        val r = capped(null)
        assertFalse(r.isCapped)
        assertEquals(d("23351"), r.amount)
    }

    @Test
    fun `budget left above the balance does not cap`() {
        val r = capped(Money(d("50000"), "INR"))
        assertFalse(r.isCapped)
        assertEquals(d("23351"), r.amount)
    }

    @Test
    fun `budget in another currency is ignored`() {
        val r = capped(Money(d("50"), "USD"))
        assertFalse(r.isCapped)
        assertEquals(d("23351"), r.amount)
    }

    @Test
    fun `budget overspent caps at zero`() {
        val r = capped(Money(d("-1200"), "INR"))
        assertTrue(r.isCapped)
        assertEquals(BigDecimal.ZERO, r.amount)
        assertNull(r.perDay)
    }

    @Test
    fun `bills come from active expense subscriptions with a date`() {
        fun sub(name: String, date: LocalDate?, state: SubscriptionState = SubscriptionState.ACTIVE, dir: SubscriptionDirection = SubscriptionDirection.EXPENSE) =
            SubscriptionEntity(merchantName = name, amount = d("100"), nextPaymentDate = date, state = state, direction = dir)
        val bills = SafeToSpend.billsFrom(
            listOf(
                sub("Netflix", today),
                sub("No date", null),
                sub("Ended", today, state = SubscriptionState.ENDED),
                sub("Salary", today, dir = SubscriptionDirection.INCOME),
            )
        )
        assertEquals(listOf("Netflix"), bills.map { it.name })
    }
}
