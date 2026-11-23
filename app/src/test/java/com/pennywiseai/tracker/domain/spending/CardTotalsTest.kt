package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.utils.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class CardTotalsTest {

    private fun d(s: String) = BigDecimal(s)
    private fun date(s: String) = LocalDate.parse(s)

    private fun card(
        last4: String,
        owes: String,
        limit: String? = null,
        currency: String = "INR",
        bill: String? = null,
    ) = AccountBalanceEntity(
        bankName = "Test Bank", accountLast4 = last4, balance = d(owes),
        timestamp = date("2026-03-20").atStartOfDay(), isCreditCard = true, currency = currency,
        creditLimit = limit?.let(::d), statementDay = 12, cardDueDay = 2,
        cardBillAmount = bill?.let(::d), cardBillAsOf = bill?.let { date("2026-03-14").atTime(9, 0) },
    )

    private fun assertTotals(expected: Map<String, String>, actual: Map<String, Money>) {
        assertEquals(expected.keys, actual.keys)
        expected.forEach { (currency, amount) ->
            assertEquals(currency, actual.getValue(currency).currency)
            assertEquals("$currency total", 0, d(amount).compareTo(actual.getValue(currency).amount))
        }
    }

    @Test
    fun `unused limit is totalled per currency, never across them`() {
        val cards = listOf(
            card("0001", owes = "2000", limit = "10000"),
            card("0002", owes = "500", limit = "5000"),
            card("0003", owes = "100", limit = "1000", currency = "USD"),
        )

        assertTotals(mapOf("INR" to "12500", "USD" to "900"), CardTotals.unusedLimit(cards))
    }

    @Test
    fun `a card with no limit is left out of unused limit, not counted as a negative`() {
        val cards = listOf(
            card("0001", owes = "2000", limit = "10000"),
            card("0002", owes = "3000", limit = null),
            card("0003", owes = "400", limit = "0"),
        )

        assertTotals(mapOf("INR" to "8000"), CardTotals.unusedLimit(cards))
        assertFalse(CardTotals.hasLimit(cards[1]))
        assertFalse(CardTotals.hasLimit(cards[2]))
        assertTrue(CardTotals.hasLimit(cards[0]))
    }

    @Test
    fun `a card over its limit adds nothing to unused limit`() {
        val cards = listOf(
            card("0001", owes = "1200", limit = "1000"),
            card("0002", owes = "0", limit = "5000"),
        )

        assertTotals(mapOf("INR" to "5000"), CardTotals.unusedLimit(cards))
    }

    @Test
    fun `unused limit follows the currency it is given`() {
        // Accounts lists each card in its resolved currency, as its "Owed on cards" total does.
        val cards = listOf(card("0001", owes = "10", limit = "100", currency = "INR"))

        assertTotals(mapOf("AED" to "90"), CardTotals.unusedLimit(cards) { "AED" })
    }

    @Test
    fun `unpaid bills are totalled per currency and leave out cards with no bill`() {
        val today = date("2026-03-20")
        val statuses = listOf(
            card("0001", owes = "5000", bill = "4000"),
            card("0002", owes = "1500", bill = "1000"),
            card("0003", owes = "300", bill = "250", currency = "USD"),
            card("0004", owes = "9000", bill = null),
        ).map { CardBills.status(it, emptyList(), today) }

        assertTotals(mapOf("INR" to "5000", "USD" to "250"), CardTotals.unpaidBills(statuses))
    }

    @Test
    fun `a paid-off bill counts as zero in its own currency`() {
        val card = card("0001", owes = "0", currency = "USD", bill = "250")
        val payment = TransactionEntity(
            id = 1, amount = d("250"), merchantName = "Payment", category = "Bills",
            transactionType = TransactionType.INCOME,
            dateTime = date("2026-03-16").atTime(12, 0), currency = "USD", transactionHash = "h1",
            bankName = "Test Bank", accountNumber = "0001",
        )
        val status = CardBills.status(card, listOf(payment), date("2026-03-20"))

        assertTrue(status.isKnown)
        assertTotals(mapOf("USD" to "0"), CardTotals.unpaidBills(listOf(status)))
    }
}
