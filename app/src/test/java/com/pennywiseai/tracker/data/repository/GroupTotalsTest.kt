package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** #837: a group's totals, shared by the Home card, the list and the detail screen. */
class GroupTotalsTest {

    private fun tx(amount: String, type: TransactionType, currency: String = "INR") = TransactionEntity(
        amount = BigDecimal(amount), merchantName = "Test", category = "Others",
        transactionType = type, dateTime = LocalDateTime.of(2026, 9, 25, 12, 0),
        transactionHash = "h$amount$type$currency", currency = currency
    )

    @Test
    fun `investments get their own total instead of vanishing`() {
        val totals = groupTotalsOf(listOf(tx("1000", TransactionType.INVESTMENT), tx("2500", TransactionType.INVESTMENT)))
        assertEquals(0, BigDecimal("3500").compareTo(totals.invested.getValue("INR").amount))
        assertTrue(totals.expense.isEmpty())
        assertTrue(totals.income.isEmpty())
    }

    @Test
    fun `investments are not counted as spending`() {
        val totals = groupTotalsOf(listOf(tx("300", TransactionType.EXPENSE), tx("1000", TransactionType.INVESTMENT)))
        assertEquals(0, BigDecimal("300").compareTo(totals.expense.getValue("INR").amount))
        assertEquals(0, BigDecimal("1000").compareTo(totals.invested.getValue("INR").amount))
    }

    @Test
    fun `invested totals stay per currency`() {
        val totals = groupTotalsOf(listOf(tx("100", TransactionType.INVESTMENT, "USD"), tx("5000", TransactionType.INVESTMENT)))
        assertEquals(setOf("USD", "INR"), totals.invested.keys)
    }

    @Test
    fun `transfers stay out of every figure`() {
        val totals = groupTotalsOf(listOf(tx("700", TransactionType.TRANSFER)))
        assertTrue(totals.expense.isEmpty() && totals.income.isEmpty() && totals.invested.isEmpty())
    }

    @Test
    fun `account shares split per account and per currency, largest first`() {
        fun at(t: TransactionEntity, bank: String?, last4: String?) = t.copy(bankName = bank, accountNumber = last4)
        val shares = accountSharesOf(listOf(
            at(tx("200", TransactionType.EXPENSE), "HDFC", "1234"),
            at(tx("50", TransactionType.EXPENSE, "USD"), "HDFC", "1234"),
            at(tx("900", TransactionType.EXPENSE), "SBI", "5678"),
            at(tx("100", TransactionType.INCOME), "SBI", "5678"),
            at(tx("40", TransactionType.EXPENSE), null, null),
            at(tx("700", TransactionType.TRANSFER), "Axis", "0001")
        ))

        assertEquals(listOf("SBI ••5678", "HDFC ••1234", null), shares.map { it.label })
        assertEquals(0, BigDecimal("100").compareTo(shares[0].totals.income.getValue("INR").amount))
        assertEquals(setOf("INR", "USD"), shares[1].totals.expense.keys)
        assertEquals(0, BigDecimal("50").compareTo(shares[1].totals.expense.getValue("USD").amount))
        assertEquals(0, BigDecimal("40").compareTo(shares[2].totals.expense.getValue("INR").amount))
    }
}
