package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class CurrencyGroupedTotalsTest {

    private fun tx(
        amount: String,
        type: TransactionType = TransactionType.EXPENSE,
        currency: String = "USD",
        impact: BudgetImpactType? = null
    ) = TransactionEntity(
        amount = BigDecimal(amount), merchantName = "Synthetic Shop", category = "Food",
        transactionType = type, dateTime = LocalDateTime.of(2026, 1, 1, 12, 0),
        transactionHash = "synthetic", currency = currency, budgetImpactType = impact
    )

    private fun assertSame(expected: String, actual: BigDecimal?) =
        assertEquals(0, BigDecimal(expected).compareTo(actual))

    @Test
    fun sumsWithoutBinaryFloatingPointDrift() {
        // As doubles this is 0.30000000000000004.
        val totals = calculateCurrencyGroupedTotals(listOf(tx("0.1"), tx("0.2")))
        assertSame("0.3", totals.totalsByCurrency.getValue("USD").expenses)
    }

    @Test
    fun keepsLargeAmountsExact() {
        // Beyond a double's 15-16 significant digits: the cents would be lost.
        val totals = calculateCurrencyGroupedTotals(listOf(tx("90071992547409.93"), tx("0.01")))
        assertSame("90071992547409.94", totals.totalsByCurrency.getValue("USD").expenses)
    }

    @Test
    fun aMarkedTransferPairCountsOnce() {
        val leg = tx("877.844", TransactionType.TRANSFER).copy(transferPairId = "pair")
        val totals = calculateCurrencyGroupedTotals(listOf(leg.copy(id = 1), leg.copy(id = 2), tx("10", TransactionType.TRANSFER)))
        assertSame("887.844", totals.totalsByCurrency.getValue("USD").transfer)
    }

    @Test
    fun neverAddsAcrossCurrencies() {
        val totals = calculateCurrencyGroupedTotals(listOf(tx("500", currency = "INR"), tx("10")))
        assertSame("500", totals.totalsByCurrency.getValue("INR").expenses)
        assertSame("10", totals.totalsByCurrency.getValue("USD").expenses)
    }

    @Test
    fun refundShrinksExpensesAndIsFlooredAtZero() {
        val refund = tx("15.50", TransactionType.INCOME, impact = BudgetImpactType.DEDUCT_SPENT)
        val income = tx("100.25", TransactionType.INCOME)
        val usd = calculateCurrencyGroupedTotals(listOf(tx("10"), refund, income))
            .totalsByCurrency.getValue("USD")
        assertSame("0", usd.expenses)
        assertSame("100.25", usd.income)
    }

    @Test
    fun excludedAndFullyLoanLinkedRowsAreLeftOut() {
        val totals = calculateCurrencyGroupedTotals(
            listOf(tx("7"), tx("100").copy(excludedFromAnalytics = true), tx("200").copy(loanId = 1))
        )
        assertSame("7", totals.totalsByCurrency.getValue("USD").expenses)
        assertEquals(1, totals.totalsByCurrency.getValue("USD").transactionCount)
        assertEquals(1, totals.transactionCount)
    }

    @Test
    fun creditTransferAndInvestmentKeepTheirOwnBuckets() {
        val usd = calculateCurrencyGroupedTotals(
            listOf(tx("1", TransactionType.CREDIT), tx("2", TransactionType.TRANSFER), tx("3", TransactionType.INVESTMENT))
        ).totalsByCurrency.getValue("USD")
        assertSame("1", usd.credit)
        assertSame("2", usd.transfer)
        assertSame("3", usd.investment)
        assertSame("0", usd.expenses)
    }

    @Test
    fun availableCurrenciesPutRupeesFirstThenAlphabetical() {
        val totals = calculateCurrencyGroupedTotals(listOf(tx("1"), tx("1", currency = "INR"), tx("1", currency = "AED")))
        assertEquals(listOf("INR", "AED", "USD"), totals.availableCurrencies)
    }
}
