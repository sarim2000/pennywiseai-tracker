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
}
