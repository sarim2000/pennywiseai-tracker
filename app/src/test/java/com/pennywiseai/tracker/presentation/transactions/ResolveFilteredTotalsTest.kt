package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.presentation.common.CurrencyGroupedTotals
import com.pennywiseai.tracker.presentation.common.CurrencyTotals
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class ResolveFilteredTotalsTest {

    private val convertedPairs = mutableListOf<Pair<String, String>>()

    /** Multiplies by the rate given for the source currency; no rate means unconvertible. */
    private fun rates(vararg rates: Pair<String, String>): suspend (BigDecimal, String, String) -> BigDecimal? {
        val byCurrency = rates.toMap()
        return { amount, from, to ->
            convertedPairs += from to to
            byCurrency[from]?.let { amount * BigDecimal(it) }
        }
    }

    private fun bucket(
        currency: String,
        income: String = "0",
        expenses: String = "0",
        credit: String = "0",
        transfer: String = "0",
        investment: String = "0",
        count: Int = 1,
    ) = currency to CurrencyTotals(
        currency = currency,
        income = BigDecimal(income),
        expenses = BigDecimal(expenses),
        credit = BigDecimal(credit),
        transfer = BigDecimal(transfer),
        investment = BigDecimal(investment),
        transactionCount = count,
    )

    private fun grouped(vararg buckets: Pair<String, CurrencyTotals>) =
        CurrencyGroupedTotals(totalsByCurrency = mapOf(*buckets))

    private fun assertSame(expected: String, actual: BigDecimal) =
        assertEquals("$actual", 0, BigDecimal(expected).compareTo(actual))

    private val mixed = grouped(
        bucket("INR", income = "1000", expenses = "500", credit = "200", count = 2),
        bucket("USD", income = "10", expenses = "4", credit = "1", count = 3),
    )

    @Test
    fun `one currency's view is only that currency's bucket`() = runTest {
        val totals = resolveFilteredTotals(mixed, "USD", isUnified = false, rates("INR" to "0.01"))
        assertSame("10", totals.income)
        assertSame("4", totals.expenses)
        assertSame("1", totals.credit)
        assertSame("6", totals.netBalance)
        assertEquals(3, totals.transactionCount)
        assertTrue(convertedPairs.isEmpty())
    }

    @Test
    fun `a currency with no bucket is all zeros`() = runTest {
        val totals = resolveFilteredTotals(mixed, "AED", isUnified = false, rates())
        assertSame("0", totals.income)
        assertSame("0", totals.expenses)
        assertEquals(0, totals.transactionCount)
    }

    @Test
    fun `unified converts foreign buckets and adds the display currency as is`() = runTest {
        val totals = resolveFilteredTotals(mixed, "USD", isUnified = true, rates("INR" to "0.01"))
        assertSame("20", totals.income)
        assertSame("9", totals.expenses)
        assertSame("3", totals.credit)
        assertEquals(5, totals.transactionCount)
    }

    @Test
    fun `unified stays exact`() = runTest {
        val totals = resolveFilteredTotals(
            grouped(bucket("USD", expenses = "0.1"), bucket("EUR", expenses = "0.2")),
            "USD",
            isUnified = true,
            rates("EUR" to "1"),
        )
        // As doubles this is 0.30000000000000004.
        assertSame("0.3", totals.expenses)
    }

    @Test
    fun `a currency with no rate adds no amount but keeps its rows counted`() = runTest {
        val totals = resolveFilteredTotals(
            grouped(bucket("USD", expenses = "5", count = 1), bucket("XXX", expenses = "100", count = 4)),
            "USD",
            isUnified = true,
            rates(),
        )
        assertSame("5", totals.expenses)
        assertEquals(5, totals.transactionCount)
    }

    @Test
    fun `unified converts a set holding only a foreign currency`() = runTest {
        val totals = resolveFilteredTotals(grouped(bucket("USD", expenses = "2")), "INR", isUnified = true, rates("USD" to "83"))
        assertSame("166", totals.expenses)
    }

    @Test
    fun `unified net is income less expenses only`() = runTest {
        val totals = resolveFilteredTotals(
            grouped(bucket("USD", income = "100", expenses = "30", credit = "7", transfer = "11", investment = "13")),
            "USD",
            isUnified = true,
            rates(),
        )
        assertSame("70", totals.netBalance)
    }

    @Test
    fun `the display currency is never sent to the converter`() = runTest {
        resolveFilteredTotals(mixed, "USD", isUnified = true, rates("INR" to "0.01"))
        assertTrue(convertedPairs.isNotEmpty())
        assertTrue(convertedPairs.all { it == ("INR" to "USD") })
    }

    @Test
    fun `an unfiltered mixed list never adds rupees to dollars`() = runTest {
        fun row(amount: String, currency: String) = TransactionEntity(
            amount = BigDecimal(amount),
            merchantName = "Synthetic Shop",
            category = "Food",
            transactionType = TransactionType.EXPENSE,
            dateTime = LocalDateTime.of(2026, 1, 1, 12, 0),
            transactionHash = "resolve-$currency",
            currency = currency,
        )
        val groupedRows = calculateCurrencyGroupedTotals(listOf(row("500", "INR"), row("10", "USD")))
        val totals = resolveFilteredTotals(groupedRows, "USD", isUnified = false, rates())
        assertSame("10", totals.expenses)
        assertEquals(1, totals.transactionCount)
    }
}
