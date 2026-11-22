package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class TransactionSortTest {

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
}
