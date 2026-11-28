package com.pennywiseai.tracker.widget

import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.ledger.breakdownByCurrency
import com.pennywiseai.tracker.utils.Money
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class CategoryPieWidgetTest {

    private fun spend(vararg amounts: Pair<String, String>) =
        amounts.associate { (currency, amount) -> currency to Money(BigDecimal(amount), currency) }

    /** USD is worth 83 INR. Anything else has no rate. */
    private fun currencyOfPie(spend: Map<String, Money>, base: String) = runBlocking {
        pieCurrency(spend, base) { money ->
            when (money.currency) {
                "USD" -> money.amount * BigDecimal(83)
                "INR" -> money.amount
                else -> null
            }
        }
    }

    @Test
    fun `the base currency wins even when a foreign face value is bigger`() {
        assertEquals("JOD", currencyOfPie(spend("INR" to "5000", "JOD" to "40"), "JOD"))
    }

    @Test
    fun `with no spend in the base currency the largest value wins, not the largest number`() {
        assertEquals("USD", currencyOfPie(spend("INR" to "5000", "USD" to "500"), "JOD"))
    }

    @Test
    fun `a currency with no rate ranks below one that has a rate`() {
        assertEquals("INR", currencyOfPie(spend("XAF" to "90000", "INR" to "10"), "JOD"))
    }

    @Test
    fun `with no rate at all the largest face value wins`() {
        assertEquals("XAF", currencyOfPie(spend("XOF" to "10", "XAF" to "90"), "JOD"))
    }

    @Test
    fun `a zero base-currency total doesn't take over the pie`() {
        assertEquals("USD", currencyOfPie(spend("USD" to "90", "JOD" to "0"), "JOD"))
    }

    @Test
    fun `a lowercase base currency still comes back as its code`() {
        assertEquals("JOD", currencyOfPie(spend("JOD" to "40"), "jod"))
    }

    @Test
    fun `with no spend at all it falls back to the base currency`() {
        assertEquals("JOD", currencyOfPie(emptyMap(), "JOD"))
    }

    @Test
    fun `only colors a theme background would swallow follow the text color`() {
        assertTrue(isLowContrast(0xFF000000.toInt()))
        assertTrue(isLowContrast(0xFF1C1B1F.toInt()))
        assertTrue(isLowContrast(0xFFFFFFFF.toInt()))
        // The usual category colors and the grey "Other" keep their own.
        listOf(0xFFFF7043, 0xFF42A5F5, 0xFFAB47BC, 0xFF26A69A, 0xFF9E9E9E, 0xFF424242).forEach {
            assertFalse(it.toString(16), isLowContrast(it.toInt()))
        }
    }

    private fun row(
        id: Long,
        amount: String,
        category: String,
        type: TransactionType = TransactionType.EXPENSE,
        currency: String = "JOD",
        refundOf: String? = null
    ) = TransactionWithSplits(
        TransactionEntity(
            id = id, amount = BigDecimal(amount), merchantName = "Demo", category = category,
            transactionType = type, dateTime = LocalDateTime.of(2026, 11, 10, 12, 0),
            transactionHash = "h$id", currency = currency,
            budgetCategory = refundOf, budgetImpactType = refundOf?.let { BudgetImpactType.DEDUCT_SPENT }
        ),
        emptyList()
    )

    /** In JOD only, as the pie is outside unified mode. */
    private fun spendOf(rows: List<TransactionWithSplits>, creditAsExpense: Boolean = false) = runBlocking {
        pieSpend(rows, creditAsExpense) { tx, amount -> amount.takeIf { tx.currency == "JOD" } }
    }

    @Test
    fun `a refund comes off the total, so it matches Home's Spent`() {
        val rows = listOf(
            row(1, "1000.000", "Groceries"),
            row(2, "419.730", "Food"),
            row(3, "4.000", "Groceries", TransactionType.INCOME, refundOf = "Groceries"),
            row(4, "900.000", "Salary", TransactionType.INCOME)
        )
        val spend = spendOf(rows)
        assertEquals(0, BigDecimal("1415.730").compareTo(spend.total))
        assertEquals(0, breakdownByCurrency(rows.map { it.transaction }).getValue("JOD").expenses.compareTo(spend.total))
        assertEquals(0, BigDecimal("996.000").compareTo(spend.byCategory.getValue("Groceries")))
        assertEquals(0, BigDecimal("419.730").compareTo(spend.byCategory.getValue("Food")))
    }

    @Test
    fun `a refund larger than its category empties the slice and still comes off the total in full`() {
        val spend = spendOf(
            listOf(
                row(1, "10", "Groceries"),
                row(2, "50", "Food"),
                row(3, "15", "Groceries", TransactionType.INCOME, refundOf = "Groceries")
            )
        )
        assertEquals(setOf("Food"), spend.byCategory.keys)
        assertEquals(0, BigDecimal("45").compareTo(spend.total))
    }

    @Test
    fun `card spend counts only when the user counts it as expense`() {
        val rows = listOf(row(1, "20", "Food"), row(2, "30", "Shopping", TransactionType.CREDIT))
        assertEquals(0, BigDecimal("20").compareTo(spendOf(rows).total))
        assertEquals(0, BigDecimal("50").compareTo(spendOf(rows, creditAsExpense = true).total))
    }

    @Test
    fun `a refund in another currency doesn't touch the pie`() {
        val spend = spendOf(
            listOf(
                row(1, "20", "Food"),
                row(2, "5", "Food", TransactionType.INCOME, currency = "USD", refundOf = "Food")
            )
        )
        assertEquals(0, BigDecimal("20").compareTo(spend.total))
    }

    /** Unified mode in JOD: USD converts at 0.7, as the worker's amountIn does with a rate. */
    private fun unifiedSpendOf(rows: List<TransactionWithSplits>) = runBlocking {
        pieSpend(rows, creditAsExpense = false) { tx, amount ->
            when (tx.currency) {
                "JOD" -> amount
                "USD" -> amount * BigDecimal("0.7")
                else -> null
            }
        }
    }

    @Test
    fun `in unified mode a refund in a currency with no spend leaves the total alone, like Home`() {
        val spend = unifiedSpendOf(
            listOf(
                row(1, "100", "Food"),
                row(2, "10", "Food", TransactionType.INCOME, currency = "USD", refundOf = "Food")
            )
        )
        assertEquals(0, BigDecimal("100").compareTo(spend.total))
    }

    @Test
    fun `in unified mode a refund comes off its own currency's spend`() {
        val spend = unifiedSpendOf(
            listOf(
                row(1, "100", "Food"),
                row(2, "20", "Shopping", currency = "USD"),
                row(3, "10", "Shopping", TransactionType.INCOME, currency = "USD", refundOf = "Shopping")
            )
        )
        // JOD 100 + (USD 20 - USD 10) at 0.7
        assertEquals(0, BigDecimal("107").compareTo(spend.total))
        assertEquals(0, BigDecimal("7").compareTo(spend.byCategory.getValue("Shopping")))
    }
}
