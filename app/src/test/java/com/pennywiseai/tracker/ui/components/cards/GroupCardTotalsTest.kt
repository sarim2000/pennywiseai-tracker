package com.pennywiseai.tracker.ui.components.cards

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.utils.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class GroupCardTotalsTest {

    private var nextId = 1L

    private fun txn(amount: String, currency: String, type: TransactionType = TransactionType.EXPENSE): TransactionEntity {
        val id = nextId++
        return TransactionEntity(
            id = id,
            amount = BigDecimal(amount),
            merchantName = "Test shop",
            category = "Food",
            transactionType = type,
            dateTime = LocalDate.of(2026, 1, 1).atTime(12, 0),
            transactionHash = "test-$id",
            currency = currency
        )
    }

    private fun Map<String, Money>.plain() =
        mapValues { it.value.amount.stripTrailingZeros().toPlainString() }

    @Test
    fun singleCurrency_netsExpenseAgainstIncome() {
        val rows = listOf(txn("100", "INR"), txn("30", "INR", TransactionType.INCOME))
        assertEquals(mapOf("INR" to "-70"), groupCardTotals(rows, emptyMap(), null).plain())
    }

    @Test
    fun mixedCurrencies_areTotalledSeparately() {
        val rows = listOf(
            txn("412", "INR"), txn("415", "INR"), txn("416", "INR"),
            txn("21", "JOD", TransactionType.INCOME)
        )
        assertEquals(mapOf("INR" to "-1243", "JOD" to "21"), groupCardTotals(rows, emptyMap(), null).plain())
    }

    @Test
    fun unifiedMode_usesConvertedAmounts() {
        val inr = txn("100", "INR")
        val usd = txn("2", "USD")
        val totals = groupCardTotals(listOf(inr, usd), mapOf(usd.id to BigDecimal("170")), "INR")
        assertEquals(mapOf("INR" to "-270"), totals.plain())
    }

    @Test
    fun unifiedMode_skipsForeignRowWithoutRate() {
        val totals = groupCardTotals(listOf(txn("100", "INR"), txn("2", "USD")), emptyMap(), "INR")
        assertEquals(mapOf("INR" to "-100"), totals.plain())
    }

    @Test
    fun emptyGroup_isZeroInFallbackCurrency() {
        assertEquals(mapOf("INR" to "0"), groupCardTotals(emptyList(), emptyMap(), null).plain())
    }
}
