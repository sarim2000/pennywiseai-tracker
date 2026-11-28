package com.pennywiseai.tracker.presentation.analytics

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.utils.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** #1398: the Analytics tag filter narrows every figure to the tagged rows. */
class AnalyticsTagFilterTest {
    private fun row(id: Long, amount: String, category: String, currency: String = "INR"): CountedRow {
        val tx = TransactionEntity(
            id = id, amount = BigDecimal(amount), merchantName = "Test", category = category,
            transactionType = TransactionType.EXPENSE, dateTime = LocalDateTime.of(2026, 10, 5, 12, 0),
            transactionHash = "h$id", currency = currency
        )
        return CountedRow(tx, Money(tx.amount, currency))
    }

    private val tags = mapOf(1L to listOf("Needs"), 2L to listOf("Needs", "Trip"), 3L to listOf("Trip"), 4L to listOf("Needs"))
    private val rows = listOf(
        row(1, "100", "Food"), row(2, "40", "Food"), row(3, "900", "Fun"),
        row(4, "10", "Bills", "USD"), row(5, "7", "Food")
    )

    private fun byCategory(list: List<CountedRow>) = groupTotals(list) { listOf(it.category) }

    @Test
    fun `a selected tag totals and groups only the tagged rows`() {
        val tagged = filterByTag(rows, "Needs", tags) { it.tx.id }
        val groups = byCategory(tagged)
        assertEquals(setOf("Food", "Bills"), groups.keys)
        assertEquals(BigDecimal("140"), groups.getValue("Food").amountIn("INR"))
        assertEquals(BigDecimal("10"), groups.getValue("Bills").amountIn("USD"))
    }

    @Test
    fun `currencies stay separate under the filter`() {
        val tagged = filterByTag(rows, "Needs", tags) { it.tx.id }
        val all = groupTotals(tagged) { listOf("all") }.getValue("all")
        assertEquals(BigDecimal("140"), all.amountIn("INR"))
        assertEquals(BigDecimal("10"), all.amountIn("USD"))
    }

    @Test
    fun `no tag selected keeps every row`() {
        assertEquals(rows, filterByTag(rows, null, tags) { it.tx.id })
    }

    @Test
    fun `grouping by tag under a filter keeps a two-tag row under each of its tags`() {
        val tagged = filterByTag(rows, "Needs", tags) { it.tx.id }
        val groups = groupTotals(tagged) { tagKeys(tags[it.id]) }
        assertEquals(BigDecimal("140"), groups.getValue("Needs").amountIn("INR"))
        assertEquals(BigDecimal("40"), groups.getValue("Trip").amountIn("INR"))
    }

    @Test
    fun `a tag that disappears and reappears does not switch the filter back on`() {
        var stored: String? = "Trip"
        stored = effectiveTagFilter(stored, listOf("Work")) // last "Trip" removed
        assertNull(stored)
        stored = effectiveTagFilter(stored, listOf("Work", "Trip")) // "Trip" created again
        assertNull(stored)
    }

    @Test
    fun `drill-down requires the filter tag plus the row's different tag`() {
        assertEquals("Work" to "Trip", drillDownTags("Trip", "Work"))
        assertEquals("work" to null, drillDownTags("work", "Work"))
        assertEquals("Trip" to null, drillDownTags("Trip", null))
        assertEquals("Work" to null, drillDownTags(null, "Work"))
    }

    @Test
    fun `a deleted selected tag falls back to all tags`() {
        assertEquals("Needs", effectiveTagFilter("Needs", listOf("Needs", "Trip")))
        assertNull(effectiveTagFilter("Needs", listOf("Trip")))
        assertNull(effectiveTagFilter(null, listOf("Trip")))
    }
}
