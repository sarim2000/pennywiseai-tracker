package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class RecurringSuggestionsTest {

    private val today = LocalDate.of(2026, 11, 2)
    private var nextId = 1L

    private fun charge(
        date: LocalDate,
        amount: String,
        merchant: String = "StreamCo",
        category: String = "Entertainment",
        currency: String = "USD",
        type: TransactionType = TransactionType.EXPENSE,
    ) = TransactionEntity(
        id = nextId++,
        amount = BigDecimal(amount),
        merchantName = merchant,
        category = category,
        transactionType = type,
        dateTime = date.atTime(9, 0),
        currency = currency,
        transactionHash = "h$nextId",
    )

    private fun detect(
        transactions: List<TransactionEntity>,
        covered: Set<String> = emptySet(),
        dismissed: Set<String> = emptySet(),
    ) = RecurringSuggestions.detect(
        transactions = transactions,
        today = today,
        builtinKeyOf = { it },
        isCovered = { merchant, currency -> RecurringSuggestions.keyFor(merchant, currency) in covered },
        dismissed = dismissed,
    )

    private fun monthly(day: LocalDate, count: Int, amount: String, merchant: String = "StreamCo", category: String = "Entertainment") =
        (0 until count).map { charge(day.minusMonths(it.toLong()), amount, merchant, category) }

    @Test
    fun `three monthly payments at the same amount are suggested`() {
        val result = detect(monthly(LocalDate.of(2026, 10, 15), 3, "15.49"))

        assertEquals(1, result.size)
        with(result.single()) {
            assertEquals("Monthly", billingCycle)
            assertEquals(BigDecimal("15.49"), amount)
            assertEquals(3, payments.size)
            assertEquals(LocalDate.of(2026, 11, 15), nextDueDate)
            assertEquals(false, isVariable)
        }
    }

    @Test
    fun `two monthly payments are not enough`() {
        assertTrue(detect(monthly(LocalDate.of(2026, 10, 15), 2, "15.49")).isEmpty())
    }

    @Test
    fun `a payday that drifts a few days still counts as monthly`() {
        val result = detect(
            listOf(
                charge(LocalDate.of(2026, 8, 3), "9.99"),
                charge(LocalDate.of(2026, 9, 1), "9.99"),
                charge(LocalDate.of(2026, 10, 4), "9.99"),
            )
        )
        assertEquals("Monthly", result.single().billingCycle)
    }

    @Test
    fun `a bill on the 31st survives short months`() {
        val result = detect(
            listOf(
                charge(LocalDate.of(2026, 7, 31), "20"),
                charge(LocalDate.of(2026, 8, 31), "20"),
                charge(LocalDate.of(2026, 9, 30), "20"),
                charge(LocalDate.of(2026, 10, 31), "20"),
            )
        )
        assertEquals(4, result.single().payments.size)
    }

    @Test
    fun `frequent same-amount top-ups are not a subscription`() {
        // A transit card topped up with the same amount a few times a month.
        val topUps = (0 until 12).map { charge(today.minusDays(it * 9L + 1), "10.09", merchant = "City Transit", category = "Others") }
        assertTrue(detect(topUps).isEmpty())
    }

    @Test
    fun `transport, groceries and dining are never suggested`() {
        val day = LocalDate.of(2026, 10, 10)
        val result = detect(
            monthly(day, 4, "30.00", merchant = "Bus Pass", category = "Transportation") +
                monthly(day, 4, "50.00", merchant = "Corner Market", category = "Groceries") +
                monthly(day, 4, "12.00", merchant = "Cafe", category = "Food & Dining")
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `a phone bill whose amount moves is suggested as variable`() {
        val result = detect(
            listOf(
                charge(LocalDate.of(2026, 8, 5), "27.10", merchant = "Telco", category = "Mobile"),
                charge(LocalDate.of(2026, 9, 5), "31.40", merchant = "Telco", category = "Mobile"),
                charge(LocalDate.of(2026, 10, 6), "29.80", merchant = "Telco", category = "Mobile"),
            )
        )
        with(result.single()) {
            assertTrue(isVariable)
            assertEquals(BigDecimal("29.80"), amount)
            assertEquals(BigDecimal("27.10"), minAmount)
            assertEquals(BigDecimal("31.40"), maxAmount)
        }
    }

    @Test
    fun `a varying amount outside phone and utilities is not suggested`() {
        val result = detect(
            listOf(
                charge(LocalDate.of(2026, 8, 5), "27.10"),
                charge(LocalDate.of(2026, 9, 5), "31.40"),
                charge(LocalDate.of(2026, 10, 6), "29.80"),
            )
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `a utility bill that swings past a quarter is not suggested`() {
        val result = detect(
            listOf(
                charge(LocalDate.of(2026, 8, 5), "20", merchant = "Power Co", category = "Bills & Utilities"),
                charge(LocalDate.of(2026, 9, 5), "60", merchant = "Power Co", category = "Bills & Utilities"),
                charge(LocalDate.of(2026, 10, 5), "30", merchant = "Power Co", category = "Bills & Utilities"),
            )
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `a one-off purchase at the merchant doesn't hide the subscription`() {
        val result = detect(monthly(LocalDate.of(2026, 10, 15), 3, "15.49") + charge(LocalDate.of(2026, 10, 20), "4.99"))
        assertEquals(BigDecimal("15.49"), result.single().amount)
    }

    @Test
    fun `a pattern that stopped months ago is stale`() {
        assertTrue(detect(monthly(LocalDate.of(2026, 7, 1), 4, "15.49")).isEmpty())
    }

    @Test
    fun `dismissed and already tracked merchants are skipped`() {
        val history = monthly(LocalDate.of(2026, 10, 15), 3, "15.49")
        val key = RecurringSuggestions.keyFor("StreamCo", "USD")

        assertTrue(detect(history, dismissed = setOf(key)).isEmpty())
        assertTrue(detect(history, covered = setOf(key)).isEmpty())
    }

    @Test
    fun `currencies are kept apart`() {
        val history = listOf(
            charge(LocalDate.of(2026, 8, 15), "15.49", currency = "USD"),
            charge(LocalDate.of(2026, 9, 15), "15.49", currency = "EUR"),
            charge(LocalDate.of(2026, 10, 15), "15.49", currency = "USD"),
        )
        assertTrue(detect(history).isEmpty())
    }

    @Test
    fun `refunds, transfers and deleted rows don't count`() {
        val history = listOf(
            charge(LocalDate.of(2026, 8, 15), "15.49", type = TransactionType.INCOME),
            charge(LocalDate.of(2026, 9, 15), "15.49", type = TransactionType.TRANSFER),
            charge(LocalDate.of(2026, 10, 15), "15.49"),
        ) + charge(LocalDate.of(2026, 7, 15), "15.49").copy(isDeleted = true)
        assertTrue(detect(history).isEmpty())
    }

    @Test
    fun `weekly needs four payments a week apart`() {
        val weekly = (0 until 4).map { charge(LocalDate.of(2026, 10, 30).minusWeeks(it.toLong()), "5.00", merchant = "Gym Class", category = "Fitness") }
        assertEquals("Weekly", detect(weekly).single().billingCycle)
        assertTrue(detect(weekly.take(3)).isEmpty())
    }

    @Test
    fun `two annual renewals are suggested`() {
        val result = detect(
            listOf(
                charge(LocalDate.of(2025, 9, 20), "99.00", merchant = "Cloud Storage"),
                charge(LocalDate.of(2026, 9, 21), "99.00", merchant = "Cloud Storage"),
            )
        )
        assertEquals("Annual", result.single().billingCycle)
    }

    @Test
    fun `an extra same-amount top-up after the monthly ones is a frequent purchase`() {
        // Topped up on the 1st every month, plus once on the 25th.
        val history = listOf(
            charge(LocalDate.of(2026, 8, 1), "10.00", merchant = "City Transit", category = "Others"),
            charge(LocalDate.of(2026, 9, 1), "10.00", merchant = "City Transit", category = "Others"),
            charge(LocalDate.of(2026, 10, 1), "10.00", merchant = "City Transit", category = "Others"),
            charge(LocalDate.of(2026, 10, 25), "10.00", merchant = "City Transit", category = "Others"),
        )
        assertTrue(detect(history).isEmpty())
    }

    @Test
    fun `a one-off buy on the bill's day doesn't hide the bill`() {
        val history = monthly(LocalDate.of(2026, 10, 15), 3, "15.49") +
            charge(LocalDate.of(2026, 9, 15), "2.99").copy(dateTime = LocalDate.of(2026, 9, 15).atTime(18, 0))
        assertEquals(3, detect(history).single().payments.size)
    }

    @Test
    fun `a month-end bill that moved early once keeps its day`() {
        val result = detect(
            listOf(
                charge(LocalDate.of(2026, 8, 31), "20"),
                charge(LocalDate.of(2026, 9, 27), "20"),
                charge(LocalDate.of(2026, 10, 31), "20"),
            )
        )
        assertEquals(3, result.single().payments.size)
    }

    @Test
    fun `a payment a few days late is due today, not overdue`() {
        val result = detect(monthly(LocalDate.of(2026, 9, 28), 3, "15.49"))
        assertEquals(today, result.single().nextDueDate)
    }

    @Test
    fun `a duplicate message on the same day doesn't break the pattern`() {
        val history = monthly(LocalDate.of(2026, 10, 15), 3, "15.49") + charge(LocalDate.of(2026, 10, 15), "15.49")
        assertEquals(3, detect(history).single().payments.size)
    }
}
