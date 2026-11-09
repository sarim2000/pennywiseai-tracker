package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class TransactionSearchTest {

    private fun tx(
        id: Long = 1,
        merchant: String = "Synthetic Coffee",
        amount: String = "42.00",
        description: String? = null,
        reference: String? = null,
        smsBody: String? = null,
    ) = TransactionEntity(
        id = id,
        amount = BigDecimal(amount),
        merchantName = merchant,
        category = "Food",
        transactionType = TransactionType.EXPENSE,
        dateTime = LocalDateTime.of(2026, 1, 1, 12, 0),
        transactionHash = "search-$id",
        description = description,
        reference = reference,
        smsBody = smsBody,
    )

    private fun matches(
        query: String,
        row: TransactionEntity = tx(),
        tags: Map<Long, List<String>> = emptyMap(),
        aliases: Map<String, String> = emptyMap(),
    ) = filterBySearch(listOf(row), query, tags, aliases).isNotEmpty()

    @Test
    fun `merchant matches partially and ignores case`() {
        assertTrue(matches("coff"))
        assertTrue(matches("COFFEE"))
        assertFalse(matches("tea"))
    }

    @Test
    fun `description matches partially`() {
        assertTrue(matches("lunch", tx(description = "Team LUNCH with friends")))
    }

    @Test
    fun `reference matches partially`() {
        assertTrue(matches("ref-00", tx(reference = "SYN-REF-001")))
    }

    @Test
    fun `sms body matches`() {
        assertTrue(matches("debit alert", tx(smsBody = "Synthetic DEBIT ALERT for your account")))
    }

    @Test
    fun `a tag matches partially and ignores case`() {
        val tags = mapOf(1L to listOf("Home", "Work Trip"))
        assertTrue(matches("trip", tags = tags))
        assertFalse(matches("holiday", tags = tags))
    }

    @Test
    fun `another transaction's tag does not match`() {
        assertFalse(matches("trip", tags = mapOf(2L to listOf("Work Trip"))))
    }

    @Test
    fun `the merchant's alias matches`() {
        assertTrue(matches("morning", aliases = mapOf("Synthetic Coffee" to "Morning Cafe")))
    }

    @Test
    fun `another merchant's alias does not match`() {
        assertFalse(matches("morning", aliases = mapOf("Other Shop" to "Morning Cafe")))
    }

    @Test
    fun `a thousands separator or space in the query still finds the amount`() {
        assertTrue(matches("1,000", tx(amount = "1000.00")))
        assertTrue(matches("1 000", tx(amount = "1000.00")))
    }

    @Test
    fun `an amount matches partially`() {
        assertTrue(matches("250", tx(amount = "1250.50")))
        assertTrue(matches("12.5", tx(amount = "12.50")))
        assertFalse(matches("999", tx(amount = "1250.50")))
    }

    @Test
    fun `a query with letters is never read as an amount`() {
        assertFalse(matches("12a", tx(amount = "12.50")))
    }

    @Test
    fun `a row with none of the optional fields is searched without failing`() {
        assertFalse(matches("nothing here", tx(description = null, reference = null, smsBody = null)))
    }

    @Test
    fun `only matching rows stay, in their order`() {
        val rows = listOf(tx(1, "Alpha Cafe"), tx(2, "Beta Shop"), tx(3, "Gamma Cafe"))
        assertEquals(listOf(1L, 3L), filterBySearch(rows, "cafe", emptyMap(), emptyMap()).map { it.id })
    }

    @Test
    fun `a blank query is no search`() {
        val rows = listOf(tx(1), tx(2, "Beta Shop"))
        assertEquals(rows, filterBySearch(rows, "", emptyMap(), emptyMap()))
        assertEquals(rows, filterBySearch(rows, "   ", emptyMap(), emptyMap()))
    }
}
