package com.pennywiseai.tracker.domain.challenges

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ChallengeRowsTest {

    private val day = LocalDate.of(2026, 11, 16)

    private fun tx(
        id: Long,
        type: TransactionType,
        category: String = "Shopping",
        currency: String = "INR",
        splits: List<Pair<String, String>> = emptyList(),
    ) = TransactionWithSplits(
        TransactionEntity(
            id = id, amount = BigDecimal("100"), merchantName = "Shop $id", category = category,
            transactionType = type, dateTime = day.atTime(9, 0), currency = currency, transactionHash = "h$id",
        ),
        splits.map { (name, amount) -> TransactionSplitEntity(transactionId = id, category = name, amount = BigDecimal(amount)) },
    )

    @Test
    fun `a card purchase counts as spending, whatever the credit card setting`() {
        val rows = listOf(
            tx(1, TransactionType.EXPENSE),
            tx(2, TransactionType.CREDIT, currency = "AED"),
            tx(3, TransactionType.INCOME),
            tx(4, TransactionType.TRANSFER),
        )
        assertEquals(listOf(1L, 2L), challengeRows(rows, emptyMap()).map { it.id })
    }

    @Test
    fun `categories roll up to their parent, blank is Others and a zero split share spends nothing`() {
        val rows = listOf(
            tx(1, TransactionType.EXPENSE, category = "Coffee"),
            tx(2, TransactionType.EXPENSE, category = ""),
            tx(3, TransactionType.EXPENSE, splits = listOf("Groceries" to "100", "Coffee" to "0")),
        )
        val counted = challengeRows(rows, mapOf("Coffee" to "Food & Dining"))
        assertEquals(setOf("Food & Dining"), counted[0].categories)
        assertEquals(setOf("Others"), counted[1].categories)
        assertEquals(setOf("Groceries"), counted[2].categories)
        assertEquals(day, counted[0].date)
    }

    @Test
    fun `the running week's category keeps its own name after it's moved under a parent`() {
        val rows = listOf(tx(1, TransactionType.EXPENSE, category = "Coffee"), tx(2, TransactionType.EXPENSE, category = "Tea"))
        val counted = challengeRows(rows, mapOf("Coffee" to "Drinks", "Tea" to "Drinks"), keep = "Coffee")
        assertEquals(setOf("Coffee"), counted[0].categories)
        assertEquals(setOf("Drinks"), counted[1].categories)
    }
}
