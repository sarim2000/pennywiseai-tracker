package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class TransactionSortAndDateGroupTest {

    private val today = LocalDate.of(2026, 11, 9)

    private fun tx(
        id: Long,
        at: LocalDateTime = today.atTime(12, 0),
        amount: String = "10",
        merchant: String = "Synthetic Shop",
    ) = TransactionEntity(
        id = id,
        amount = BigDecimal(amount),
        merchantName = merchant,
        category = "Food",
        transactionType = TransactionType.EXPENSE,
        dateTime = at,
        transactionHash = "sort-$id",
    )

    // ─── Sort ────────────────────────────────────────────────────────────────

    private val rows = listOf(
        tx(1, today.minusDays(3).atTime(9, 0), "30", "beta"),
        tx(2, today.atTime(9, 0), "10", "Alpha"),
        tx(3, today.minusDays(1).atTime(9, 0), "20.50", "Gamma"),
    )

    private fun order(option: SortOption) = sortTransactions(rows, option).map { it.id }

    @Test
    fun `date sorts run both ways`() {
        assertEquals(listOf(2L, 3L, 1L), order(SortOption.DATE_NEWEST))
        assertEquals(listOf(1L, 3L, 2L), order(SortOption.DATE_OLDEST))
    }

    @Test
    fun `amount sorts run both ways`() {
        assertEquals(listOf(1L, 3L, 2L), order(SortOption.AMOUNT_HIGHEST))
        assertEquals(listOf(2L, 3L, 1L), order(SortOption.AMOUNT_LOWEST))
    }

    @Test
    fun `merchant sorts ignore case`() {
        assertEquals(listOf(2L, 1L, 3L), order(SortOption.MERCHANT_AZ))
        assertEquals(listOf(3L, 1L, 2L), order(SortOption.MERCHANT_ZA))
    }

    @Test
    fun `ties keep their incoming order`() {
        val tied = listOf(tx(1), tx(2), tx(3))
        SortOption.entries.forEach { option ->
            assertEquals(option.name, listOf(1L, 2L, 3L), sortTransactions(tied, option).map { it.id })
        }
    }

    // ─── Date groups ─────────────────────────────────────────────────────────

    private fun groupOf(at: LocalDateTime, on: LocalDate = today) =
        groupTransactionsByDate(listOf(tx(1, at)), on).keys.single()

    @Test
    fun `today runs from midnight to the last second`() {
        assertEquals(DateGroup.TODAY, groupOf(today.atStartOfDay()))
        assertEquals(DateGroup.TODAY, groupOf(today.atTime(23, 59, 59)))
    }

    @Test
    fun `yesterday runs from midnight to the last second`() {
        val yesterday = today.minusDays(1)
        assertEquals(DateGroup.YESTERDAY, groupOf(yesterday.atStartOfDay()))
        assertEquals(DateGroup.YESTERDAY, groupOf(yesterday.atTime(23, 59, 59)))
    }

    @Test
    fun `this week is the six days before today`() {
        assertEquals(DateGroup.THIS_WEEK, groupOf(today.minusDays(2).atTime(12, 0)))
        assertEquals(DateGroup.THIS_WEEK, groupOf(today.minusDays(6).atStartOfDay()))
    }

    @Test
    fun `a week ago and older is earlier`() {
        assertEquals(DateGroup.EARLIER, groupOf(today.minusDays(7).atTime(23, 59, 59)))
        assertEquals(DateGroup.EARLIER, groupOf(today.minusDays(8).atTime(12, 0)))
    }

    @Test
    fun `yesterday crosses a year boundary`() {
        val newYear = LocalDate.of(2026, 1, 1)
        assertEquals(DateGroup.YESTERDAY, groupOf(LocalDate.of(2025, 12, 31).atTime(23, 59, 59), on = newYear))
        assertEquals(DateGroup.THIS_WEEK, groupOf(LocalDate.of(2025, 12, 30).atTime(12, 0), on = newYear))
    }

    @Test
    fun `a future-dated row falls in this week`() {
        assertEquals(DateGroup.THIS_WEEK, groupOf(today.plusDays(1).atTime(12, 0)))
    }

    @Test
    fun `each group keeps the sorted order`() {
        val sorted = listOf(
            tx(1, today.atTime(9, 0)),
            tx(2, today.minusDays(1).atTime(9, 0)),
            tx(3, today.atTime(8, 0)),
            tx(4, today.minusDays(1).atTime(8, 0)),
        )
        val groups = groupTransactionsByDate(sorted, today)
        assertEquals(listOf(1L, 3L), groups.getValue(DateGroup.TODAY).map { it.id })
        assertEquals(listOf(2L, 4L), groups.getValue(DateGroup.YESTERDAY).map { it.id })
    }
}
