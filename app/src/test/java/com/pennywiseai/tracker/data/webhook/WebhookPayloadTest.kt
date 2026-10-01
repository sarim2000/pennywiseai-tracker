package com.pennywiseai.tracker.data.webhook

import com.pennywiseai.tracker.data.database.entity.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class WebhookPayloadTest {
    private val now = LocalDateTime.of(2026, 3, 1, 12, 0)
    private fun transaction(id: Long, type: TransactionType, amount: String, currency: String = "INR") = TransactionEntity(
        id = id, amount = BigDecimal(amount), merchantName = "Test merchant", category = "Food",
        transactionType = type, dateTime = now.minusYears(1), updatedAt = now, transactionHash = "test_$id", currency = currency)

    @Test fun `tombstones contain only stable id action and modification time`() {
        val payload = webhookTransactionPayload(transaction(4, TransactionType.EXPENSE, "42").copy(isDeleted = true))
        assertEquals("txn_4", payload.id)
        assertEquals("delete", payload.action)
        assertEquals(now.toString(), payload.updatedAt)
        assertNull(payload.amount)
        assertNull(payload.merchant)
    }

    @Test fun `summary excludes transfers other currencies and excluded rows and counts splits`() {
        val expense = transaction(1, TransactionType.EXPENSE, "100")
        val rows = listOf(
            TransactionWithSplits(expense, listOf(
                TransactionSplitEntity(transactionId = 1, amount = BigDecimal("60"), category = "Food"),
                TransactionSplitEntity(transactionId = 1, amount = BigDecimal("40"), category = "Travel"))),
            TransactionWithSplits(transaction(2, TransactionType.INCOME, "200"), emptyList()),
            TransactionWithSplits(transaction(3, TransactionType.TRANSFER, "999"), emptyList()),
            TransactionWithSplits(transaction(4, TransactionType.EXPENSE, "888", "USD"), emptyList()),
            TransactionWithSplits(transaction(5, TransactionType.EXPENSE, "777").copy(excludedFromAnalytics = true), emptyList()),
            TransactionWithSplits(transaction(6, TransactionType.CREDIT, "50"), emptyList()))
        val summary = webhookSummary(rows, "INR", false)
        assertEquals("200", summary.totalIncome)
        assertEquals("100", summary.totalExpense)
        assertEquals("100", summary.netAmount)
        assertEquals(mapOf("Food" to "60", "Travel" to "40"), summary.categories.associate { it.category to it.amount })
        assertEquals("150", webhookSummary(rows, "INR", true).totalExpense)
    }

    @Test fun `missing cursor for newly selected type starts at epoch`() {
        val profile = WebhookProfileEntity(name = "Test", url = "https://example.com")
        val cursors = listOf(WebhookCursorEntity(profile.id, WebhookDataType.TRANSACTIONS, now.minusHours(1)))
        val range = resolveWebhookRange(profile, setOf(WebhookDataType.TRANSACTIONS, WebhookDataType.SUMMARY), cursors, now)
        assertEquals(LocalDateTime.of(1970, 1, 1, 0, 0), range.start)
        assertEquals(now, range.end)
    }

    @Test fun `previous month handles year boundary and includes last instant`() {
        val range = resolveWebhookRange(WebhookProfileEntity(name = "Test", url = "https://example.com", rangePreset = WebhookRangePreset.PREVIOUS_MONTH),
            emptySet(), emptyList(), LocalDateTime.of(2026, 1, 1, 12, 0))
        assertEquals(LocalDateTime.of(2025, 12, 1, 0, 0), range.start)
        assertEquals(java.time.LocalDate.of(2025, 12, 31).atTime(java.time.LocalTime.MAX), range.end)
    }
}
