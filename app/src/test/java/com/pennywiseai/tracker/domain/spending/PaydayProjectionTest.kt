package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.RecurringFrequency
import com.pennywiseai.tracker.data.database.entity.RecurringTransactionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.utils.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class PaydayProjectionTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val cycleEnd = LocalDate.of(2026, 11, 30)
    private val payday = LocalDate.of(2026, 11, 25)

    private fun d(v: String) = BigDecimal(v)
    private fun bill(amount: String, day: Int, currency: String = "INR") =
        Bill("Bill $day", Money(d(amount), currency), today.withDayOfMonth(day))
    private fun salary(date: LocalDate = payday, currency: String = "INR") = ExpectedIncome("Salary", currency, date)

    private fun project(
        balance: String,
        bills: List<Bill> = emptyList(),
        incomes: List<ExpectedIncome> = listOf(salary()),
    ) = PaydayProjection.of("INR", d(balance), bills, incomes, today, cycleEnd)

    @Test
    fun `with no bills the lowest balance is today's`() {
        val p = project("5000")
        assertEquals(payday, p.until)
        assertEquals(salary(), p.income)
        assertTrue(p.bills.isEmpty())
        assertEquals(d("5000"), p.lowest)
        assertEquals(today, p.lowestOn)
        assertNull(p.belowZeroFrom)
    }

    @Test
    fun `bills before payday count, one on or after payday doesn't`() {
        val p = project("5000", listOf(bill("1200", 20), bill("800", 15), bill("3000", 25), bill("400", 28)))
        assertEquals(listOf(15, 20), p.bills.map { it.dueDate.dayOfMonth })
        assertEquals(d("3000"), p.lowest)
        assertEquals(today.withDayOfMonth(20), p.lowestOn)
        assertNull(p.belowZeroFrom)
    }

    @Test
    fun `a dip below zero is dated to the bill that causes it`() {
        val p = project("1000", listOf(bill("600", 12), bill("700", 18), bill("200", 22)))
        assertEquals(d("-500"), p.lowest)
        assertEquals(today.withDayOfMonth(22), p.lowestOn)
        assertEquals(today.withDayOfMonth(18), p.belowZeroFrom)
    }

    @Test
    fun `a balance already below zero is short from today`() {
        val p = project("-50")
        assertEquals(today, p.belowZeroFrom)
        assertEquals(d("-50"), p.lowest)
    }

    @Test
    fun `bills and income in another currency are left out`() {
        val p = project(
            "1000",
            bills = listOf(bill("300", 15), bill("5000", 12, currency = "USD")),
            // A dollar income sooner than the rupee salary isn't this page's payday.
            incomes = listOf(salary(today.plusDays(3), currency = "USD"), salary()),
        )
        assertEquals(payday, p.until)
        assertEquals(listOf("INR"), p.bills.map { it.amount.currency })
        assertEquals(d("700"), p.lowest)
        assertNull(p.belowZeroFrom)
    }

    @Test
    fun `without an income it runs to the cycle's end`() {
        val p = project("1000", listOf(bill("300", 28), bill("200", 30)), incomes = emptyList())
        assertNull(p.income)
        assertEquals(cycleEnd.plusDays(1), p.until)
        assertEquals(2, p.bills.size)
        assertEquals(d("500"), p.lowest)
    }

    @Test
    fun `an income too far out isn't payday`() {
        val p = project("1000", incomes = listOf(salary(today.plusDays(PaydayProjection.MAX_DAYS + 1))))
        assertNull(p.income)
        assertEquals(cycleEnd.plusDays(1), p.until)
    }

    @Test
    fun `an overdue card bill is still owed today, a past subscription isn't`() {
        val card = Bill("Card", Money(d("400"), "INR"), today.minusDays(2), BillSource.Card("Test Bank", "1234"))
        val p = project("1000", listOf(card, bill("900", 5)))
        assertEquals(listOf(today), p.bills.map { it.dueDate })
        assertEquals(d("600"), p.lowest)
        assertEquals(today, p.lowestOn)
    }

    @Test
    fun `on payday an overdue card bill is the income's to pay`() {
        val card = Bill("Card", Money(d("200"), "INR"), today.minusDays(2), BillSource.Card("Test Bank", "1234"))
        val p = project("100", listOf(card), incomes = listOf(salary(today)))
        assertEquals(today, p.until)
        assertTrue(p.bills.isEmpty())
        assertEquals(d("100"), p.lowest)
        assertNull(p.belowZeroFrom)
    }

    @Test
    fun `a payment charged to a credit card waits for the card's bill`() {
        val onCard = Bill("Streaming", Money(d("200"), "INR"), today.plusDays(2), BillSource.Subscription, onCreditCard = true)
        val p = project("100", listOf(onCard, bill("50", 15)))
        assertEquals(listOf(d("50")), p.bills.map { it.amount.amount })
        assertEquals(d("50"), p.lowest)
        assertNull(p.belowZeroFrom)
    }

    @Test
    fun `calculate keeps each currency apart and skips estimated balances`() {
        fun account(last4: String, balance: String, currency: String = "INR", type: String = "SAVINGS") = AccountBalanceEntity(
            bankName = "Test Bank", accountLast4 = last4, balance = d(balance),
            timestamp = today.atStartOfDay(), accountType = type, currency = currency, isCreditCard = type == "CREDIT",
        )
        val projections = PaydayProjection.calculate(
            accounts = listOf(
                account("1111", "2000"),
                account("2222", "500", type = "CURRENT"),
                account("3333", "9999"),
                account("4444", "100", currency = "USD"),
                account("5555", "50000", type = "CREDIT"),
            ),
            unanchored = setOf("Test Bank" to "3333"),
            bills = listOf(bill("1000", 15), bill("30", 15, currency = "USD")),
            incomes = listOf(salary()),
            today = today,
            cycleEnd = cycleEnd,
        ).associateBy { it.currency }
        assertEquals(setOf("INR", "USD"), projections.keys)
        val inr = projections.getValue("INR")
        assertEquals(d("2500"), inr.balance)
        assertEquals(d("1500"), inr.lowest)
        assertTrue(inr.partial)
        val usd = projections.getValue("USD")
        assertEquals(d("70"), usd.lowest)
        assertNull(usd.income)
        assertFalse(usd.partial)
    }

    @Test
    fun `incomes come from income subscriptions and templates only`() {
        val incomes = PaydayProjection.incomesFrom(
            subscriptions = listOf(
                SubscriptionEntity(
                    merchantName = "Salary", amount = d("50000"), nextPaymentDate = today.minusMonths(1).withDayOfMonth(25),
                    direction = SubscriptionDirection.INCOME,
                ),
                SubscriptionEntity(merchantName = "Streaming", amount = d("199"), nextPaymentDate = today.plusDays(2)),
            ),
            recurring = listOf(
                RecurringTransactionEntity(
                    merchantName = "Rent in", amount = d("8000"), transactionType = TransactionType.INCOME,
                    frequency = RecurringFrequency.MONTHLY, dayOfMonth = 5, nextDueDate = today.withDayOfMonth(5),
                ),
                RecurringTransactionEntity(merchantName = "Gym", amount = d("900"), nextDueDate = today.plusDays(1)),
            ),
            today = today,
        )
        assertEquals(
            listOf(salary(), ExpectedIncome("Rent in", "INR", LocalDate.of(2026, 12, 5))),
            incomes,
        )
    }
}
