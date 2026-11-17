package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class CardBillTest {

    private fun d(s: String) = BigDecimal(s)
    private fun date(s: String) = LocalDate.parse(s)
    private var nextId = 1L

    private fun card(
        owes: String = "5000",
        statementDay: Int? = 12,
        dueDay: Int? = 2,
        bill: String? = "4000",
        enteredOn: String? = "2026-03-14",
        currency: String = "INR",
    ) = AccountBalanceEntity(
        bankName = "Test Bank", accountLast4 = "0000", balance = d(owes),
        timestamp = date("2026-03-20").atStartOfDay(), isCreditCard = true, currency = currency,
        statementDay = statementDay, cardDueDay = dueDay, cardBillAmount = bill?.let(::d),
        cardBillAsOf = enteredOn?.let { date(it).atTime(9, 0) },
    )

    private fun tx(
        on: String,
        amount: String,
        type: TransactionType,
        account: String? = "0000",
        to: String? = null,
        currency: String = "INR",
        pair: String? = null,
        impact: BudgetImpactType? = null,
        merchant: String = "Payment",
        sms: String? = null,
    ) = TransactionEntity(
        id = nextId, amount = d(amount), merchantName = merchant, category = "Bills", transactionType = type,
        dateTime = date(on).atTime(12, 0), currency = currency, transactionHash = "h${nextId++}",
        bankName = "Test Bank", accountNumber = account, toAccount = to, transferPairId = pair,
        budgetImpactType = impact, smsBody = sms,
    )

    @Test
    fun `statement date is the latest close on or before the day`() {
        assertEquals(date("2026-03-12"), CardBills.statementDate(12, date("2026-03-12")))
        assertEquals(date("2026-02-12"), CardBills.statementDate(12, date("2026-03-11")))
        // A day-31 statement closes on the last day of a short month.
        assertEquals(date("2026-02-28"), CardBills.statementDate(31, date("2026-03-15")))
    }

    @Test
    fun `due date is the due day after the statement, never the statement day`() {
        assertEquals(date("2026-04-02"), CardBills.dueDate(date("2026-03-12"), 2))
        assertEquals(date("2026-03-30"), CardBills.dueDate(date("2026-03-12"), 30))
        // Same day as the statement: next month's.
        assertEquals(date("2026-04-12"), CardBills.dueDate(date("2026-03-12"), 12))
    }

    @Test
    fun `month-end and leap-year due days clamp to the month`() {
        assertEquals(date("2028-02-29"), CardBills.dueDate(date("2028-01-31"), 31))
        assertEquals(date("2027-02-28"), CardBills.dueDate(date("2027-01-31"), 31))
        assertEquals(date("2027-03-30"), CardBills.dueDate(date("2027-02-28"), 30))
    }

    @Test
    fun `an unpaid bill is fully reserved until its due date`() {
        val status = CardBills.status(card(), emptyList(), date("2026-03-20"))

        val bill = status.bill!!
        assertEquals(date("2026-03-12"), bill.statementDate)
        assertEquals(date("2026-04-02"), bill.dueDate)
        assertEquals(d("4000"), bill.remaining)
        assertFalse(bill.isOverdue(date("2026-03-20")))
        assertFalse(status.needsBill)
    }

    @Test
    fun `a partial payment leaves the rest reserved`() {
        val paid = listOf(tx("2026-03-18", "1500", TransactionType.INCOME))

        val bill = CardBills.status(card(), paid, date("2026-03-20")).bill!!

        assertEquals(d("1500"), bill.paid)
        assertEquals(d("2500"), bill.remaining)
    }

    @Test
    fun `a bill paid in full is retired`() {
        val paid = listOf(tx("2026-03-15", "3000", TransactionType.INCOME), tx("2026-03-19", "1000", TransactionType.INCOME))

        val status = CardBills.status(card(), paid, date("2026-03-20"))

        assertNull(status.bill)
        assertFalse(status.needsBill)
    }

    @Test
    fun `an overdue unpaid bill stays reserved`() {
        val status = CardBills.status(card(), emptyList(), date("2026-04-05"))

        assertTrue(status.bill!!.isOverdue(date("2026-04-05")))
        assertEquals(d("4000"), status.bill!!.remaining)
    }

    @Test
    fun `refunds, spending, other currencies and payments before the statement don't pay the bill`() {
        val noise = listOf(
            tx("2026-03-15", "500", TransactionType.INCOME, impact = BudgetImpactType.DEDUCT_SPENT),
            tx("2026-03-15", "700", TransactionType.CREDIT),
            tx("2026-03-15", "50", TransactionType.INCOME, currency = "USD"),
            tx("2026-03-10", "4000", TransactionType.INCOME),
            tx("2026-03-15", "900", TransactionType.INCOME, account = "9999"),
        )

        assertEquals(d("4000"), CardBills.status(card(), noise, date("2026-03-20")).bill!!.remaining)
    }

    @Test
    fun `an unmarked refund or cashback from an SMS doesn't pay the bill`() {
        val credits = listOf(
            tx("2026-03-15", "800", TransactionType.INCOME, merchant = "Example Store", sms = "Refund of INR 800 credited to your card"),
            tx("2026-03-16", "40", TransactionType.INCOME, merchant = "Cashback"),
            tx("2026-03-17", "120", TransactionType.INCOME, sms = "Transaction reversed on your card"),
            tx("2026-03-18", "1000", TransactionType.INCOME, sms = "Payment received on your card"),
        )

        assertEquals(d("3000"), CardBills.status(card(), credits, date("2026-03-20")).bill!!.remaining)
    }

    @Test
    fun `a transfer into the card pays it once, even with both legs recorded`() {
        val legs = listOf(
            tx("2026-03-16", "1000", TransactionType.TRANSFER, account = "1111", to = "0000", pair = "p1"),
            tx("2026-03-16", "1000", TransactionType.TRANSFER, account = "0000", to = "0000", pair = "p1"),
        )

        assertEquals(d("3000"), CardBills.status(card(), legs, date("2026-03-20")).bill!!.remaining)
    }

    @Test
    fun `no due day or amount means unknown, never guessed`() {
        val noDueDay = CardBills.status(card(dueDay = null), emptyList(), date("2026-03-20"))
        val noAmount = CardBills.status(card(bill = null, enteredOn = null), emptyList(), date("2026-03-20"))

        assertNull(noDueDay.bill)
        assertTrue(noDueDay.needsBill)
        assertNull(noAmount.bill)
        assertTrue(noAmount.needsBill)
    }

    @Test
    fun `a card that owes nothing needs no bill`() {
        assertFalse(CardBills.status(card(owes = "0", dueDay = null), emptyList(), date("2026-03-20")).needsBill)
    }

    @Test
    fun `a later statement asks for its amount and keeps an unpaid older bill`() {
        val status = CardBills.status(card(), emptyList(), date("2026-04-13"))

        assertTrue(status.needsBill)
        assertEquals(date("2026-04-02"), status.bill!!.dueDate)
    }

    @Test
    fun `entering a new amount replaces the bill with the new statement's`() {
        val status = CardBills.status(card(bill = "2200", enteredOn = "2026-04-13"), emptyList(), date("2026-04-14"))

        assertEquals(date("2026-04-12"), status.bill!!.statementDate)
        assertEquals(date("2026-05-02"), status.bill!!.dueDate)
        assertEquals(d("2200"), status.bill!!.remaining)
        assertFalse(status.needsBill)
    }

    @Test
    fun `without a statement day the bill dates from when it was entered`() {
        val bill = CardBills.status(card(statementDay = null, dueDay = 25), emptyList(), date("2026-03-20")).bill!!

        assertEquals(date("2026-03-14"), bill.statementDate)
        assertEquals(date("2026-03-25"), bill.dueDate)
    }

    @Test
    fun `without a statement day a paid bill stops being the card's latest a month on`() {
        val card = card(statementDay = null, dueDay = 25)
        val paid = listOf(tx("2026-03-20", "4000", TransactionType.INCOME))

        // Paid off, and the month isn't up: what's charged since is in the next bill.
        val sameMonth = CardBills.status(card, paid, date("2026-04-13"))
        assertNull(sameMonth.bill)
        assertTrue(sameMonth.isKnown)
        assertFalse(sameMonth.needsBill)

        // A month on, the card owes a new statement nobody has entered.
        val nextMonth = CardBills.status(card, paid, date("2026-04-14"))
        assertNull(nextMonth.bill)
        assertFalse(nextMonth.isKnown)
        assertTrue(nextMonth.needsBill)
        assertFalse(CardBills.keepsStatement(nextMonth, 25, d("4000"), date("2026-04-14")))
    }

    @Test
    fun `changing only the due day keeps the bill on its statement`() {
        val status = CardBills.status(card(), emptyList(), date("2026-03-20"))

        assertTrue(CardBills.keepsStatement(status, 5, d("4000"), date("2026-03-20")))
        assertFalse(CardBills.keepsStatement(status, 2, d("4100"), date("2026-03-20")))
    }

    @Test
    fun `the same amount after the bill is paid or a new statement closes is the new statement's`() {
        val paid = listOf(tx("2026-03-15", "4000", TransactionType.INCOME))
        val paidOff = CardBills.status(card(), paid, date("2026-03-20"))
        val newer = CardBills.status(card(), emptyList(), date("2026-04-13"))

        assertFalse(CardBills.keepsStatement(paidOff, 2, d("4000"), date("2026-03-20")))
        assertFalse(CardBills.keepsStatement(newer, 2, d("4000"), date("2026-04-13")))
    }
}
