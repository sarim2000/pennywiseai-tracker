package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class DayInOutTest {

    private fun tx(
        id: Long,
        amount: String,
        type: TransactionType = TransactionType.EXPENSE,
        currency: String = "INR",
        category: String = "Food",
        excluded: Boolean = false,
    ) = TransactionEntity(
        id = id,
        amount = BigDecimal(amount),
        merchantName = "Synthetic Shop",
        category = category,
        transactionType = type,
        dateTime = LocalDateTime.of(2026, 11, 9, 12, 0),
        transactionHash = "day-$id",
        currency = currency,
        excludedFromAnalytics = excluded,
    )

    private fun inOut(
        rows: List<TransactionEntity>,
        splitShares: CategorySplitShares? = null,
        converted: Map<Long, BigDecimal> = emptyMap(),
        displayCurrency: String? = null,
    ) = dayInOut(rows, splitShares, converted, displayCurrency)

    private fun flow(currency: String, moneyIn: String, moneyOut: String) =
        DayInOut(currency, BigDecimal(moneyIn), BigDecimal(moneyOut))

    /** BigDecimal equality is scale-sensitive; compare the figures as plain strings. */
    private fun List<DayInOut>.plain() = map {
        Triple(it.currency, it.moneyIn.stripTrailingZeros().toPlainString(), it.moneyOut.stripTrailingZeros().toPlainString())
    }

    @Test
    fun `money in and out are summed within the day's currency`() {
        val rows = listOf(
            tx(1, "200"),
            tx(2, "300"),
            tx(3, "1000", TransactionType.INCOME),
        )
        assertEquals(listOf(flow("INR", "1000", "500")).plain(), inOut(rows).plain())
    }

    @Test
    fun `each currency gets its own line and is never added to another`() {
        val rows = listOf(
            tx(1, "500"),
            tx(2, "2000", TransactionType.INCOME),
            tx(3, "40", currency = "AED"),
            tx(4, "10", TransactionType.INCOME, currency = "AED"),
        )
        assertEquals(
            listOf(flow("INR", "2000", "500"), flow("AED", "10", "40")).plain(),
            inOut(rows).plain()
        )
    }

    @Test
    fun `a currency that only went one way has no line`() {
        val rows = listOf(
            tx(1, "200"),
            tx(2, "300"),
            tx(3, "10", TransactionType.INCOME, currency = "USD"),
        )
        assertEquals(emptyList<DayInOut>(), inOut(rows))
    }

    @Test
    fun `transfers, card spend, investments and excluded rows stay out`() {
        val rows = listOf(
            tx(1, "100"),
            tx(2, "400", TransactionType.INCOME),
            tx(3, "9000", TransactionType.TRANSFER),
            tx(4, "700", TransactionType.CREDIT),
            tx(5, "800", TransactionType.INVESTMENT),
            tx(6, "50", excluded = true),
            tx(7, "60", TransactionType.INCOME, excluded = true),
        )
        assertEquals(listOf(flow("INR", "400", "100")).plain(), inOut(rows).plain())
    }

    @Test
    fun `in a category-filtered list a split row counts only its share`() {
        val shares = CategorySplitShares(
            categories = setOf("Food"),
            splits = listOf(
                TransactionSplitEntity(transactionId = 1, category = "Food", amount = BigDecimal("30")),
                TransactionSplitEntity(transactionId = 1, category = "Travel", amount = BigDecimal("70")),
            ),
        )
        val rows = listOf(tx(1, "100"), tx(2, "500", TransactionType.INCOME))
        assertEquals(listOf(flow("INR", "500", "30")).plain(), inOut(rows, splitShares = shares).plain())
    }

    @Test
    fun `unified mode counts converted figures in the display currency`() {
        val rows = listOf(
            tx(1, "100", currency = "AED"),
            tx(2, "500", TransactionType.INCOME),
        )
        val converted = mapOf(1L to BigDecimal("2270"), 2L to BigDecimal("500"))
        assertEquals(
            listOf(flow("INR", "500", "2270")).plain(),
            inOut(rows, converted = converted, displayCurrency = "INR").plain()
        )
    }

    @Test
    fun `the busiest currency comes first`() {
        val rows = listOf(
            tx(1, "1", currency = "USD"),
            tx(2, "2", TransactionType.INCOME, currency = "USD"),
            tx(3, "100"),
            tx(4, "200", TransactionType.INCOME),
        )
        assertEquals(listOf("INR", "USD"), inOut(rows).map { it.currency })
    }
}
