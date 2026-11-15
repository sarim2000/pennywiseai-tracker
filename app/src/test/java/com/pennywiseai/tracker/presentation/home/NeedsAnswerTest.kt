package com.pennywiseai.tracker.presentation.home

import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.preferences.NeedsAnswerSnoozes
import com.pennywiseai.tracker.presentation.transactions.WhatWasThis
import com.pennywiseai.tracker.presentation.transactions.whatWasThisOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class NeedsAnswerTest {
    private val today = LocalDate.of(2026, 11, 15)

    private fun txn(
        id: Long,
        type: TransactionType = TransactionType.EXPENSE,
        category: String = "Others",
        day: Int = 10,
    ) = TransactionEntity(
        id = id,
        amount = BigDecimal("25.000"),
        merchantName = "Transfer $id",
        category = category,
        transactionType = type,
        dateTime = LocalDateTime.of(2026, 11, day, 12, 0),
        currency = "JOD",
        transactionHash = "hash$id",
    )

    @Test
    fun `money out in Others and money in left as Income need an answer`() {
        assertTrue(needsAnAnswer(txn(1)))
        assertTrue(needsAnAnswer(txn(2, TransactionType.INCOME, "Income")))
    }

    @Test
    fun `placed or already answered rows don't`() {
        assertFalse(needsAnAnswer(txn(1, category = "Food & Dining")))
        assertFalse(needsAnAnswer(txn(2, TransactionType.INCOME, "Salary")))
        assertFalse(needsAnAnswer(txn(3, TransactionType.TRANSFER)))
        assertFalse(needsAnAnswer(txn(4).copy(loanId = 9)))
        assertFalse(needsAnAnswer(txn(5).copy(lentSharesTotal = BigDecimal.TEN)))
        assertFalse(needsAnAnswer(txn(6).copy(excludedFromAnalytics = true)))
        assertFalse(needsAnAnswer(txn(7).copy(isDeleted = true)))
    }

    @Test
    fun `an Others expense split across categories is answered`() {
        val rows = listOf(txn(1), txn(2))
        assertEquals(listOf(2L), needsAnswerQueue(rows, setOf(1L), emptyMap(), today).map { it.id })
    }

    @Test
    fun `income recorded as a refund is answered`() {
        val refund = txn(1, TransactionType.INCOME, "Income")
            .copy(budgetImpactType = BudgetImpactType.DEDUCT_SPENT, budgetCategory = "Food & Dining")
        assertFalse(needsAnAnswer(refund))
    }

    @Test
    fun `kept rows stay hidden and put-off rows come back after their day`() {
        val rows = listOf(txn(1, day = 3), txn(2, day = 9), txn(3, day = 12))
        val snoozed = mapOf("hash1" to NeedsAnswerSnoozes.FOREVER, "hash2" to today.toEpochDay())
        assertEquals(listOf(3L), needsAnswerQueue(rows, emptySet(), snoozed, today).map { it.id })
        assertEquals(listOf(3L, 2L), needsAnswerQueue(rows, emptySet(), snoozed, today.plusDays(1)).map { it.id })
    }

    @Test
    fun `snoozes survive a round trip and skip junk`() {
        val snoozes = mapOf("a|b" to 5L, "c" to NeedsAnswerSnoozes.FOREVER)
        assertEquals(snoozes, NeedsAnswerSnoozes.decode(NeedsAnswerSnoozes.encode(snoozes)))
        assertEquals(emptyMap<String, Long>(), NeedsAnswerSnoozes.decode(setOf("nobar", "|5", "x|notanumber")))
    }

    @Test
    fun `asking adds another category, keep and later`() {
        val options = whatWasThisOptions(txn(1), hasLoan = false, asking = true)
        assertEquals(listOf(WhatWasThis.CATEGORY, WhatWasThis.KEEP, WhatWasThis.LATER), options.takeLast(3))
        assertFalse(WhatWasThis.KEEP in whatWasThisOptions(txn(1), hasLoan = false))
    }
}
