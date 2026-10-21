package com.pennywiseai.tracker.receiver

import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** The two one-tap answers on an unsorted transaction alert. */
class SmsBroadcastReceiverQuickPicksTest {
    private val expense = listOf("Food & Dining", "Groceries", "Shopping", "Others")

    @Test
    fun `most-used categories of the transaction's type come first`() =
        // "Income" and "Banking"-style picks from the other type never show on a purchase.
        assertEquals(
            listOf("Shopping", "Groceries"),
            SmsBroadcastReceiver.quickPicks(listOf("Others", "Income", "Shopping", "Groceries"), expense, exclude = "Others")
        )

    @Test
    fun `a new install with no history still gets two buttons`() =
        assertEquals(
            listOf("Food & Dining", "Groceries"),
            SmsBroadcastReceiver.quickPicks(emptyList(), expense, exclude = "Others")
        )

    @Test
    fun `Others is the question, never an answer`() =
        assertEquals(
            listOf("Shopping"),
            SmsBroadcastReceiver.quickPicks(listOf("Others"), listOf("Others", "Shopping"), exclude = "Others")
        )

    @Test
    fun `a purchase keeps its history when a dozen income categories are used more`() = runBlocking {
        val income = (1..12).map { "Income $it" }
        val transactions = mockk<TransactionRepository>()
        // The whole ranking: a top-10 cut before the type filter would leave only income here.
        coEvery { transactions.getCategoriesByUsage() } returns income + listOf("Others", "Shopping", "Groceries")
        val categories = mockk<CategoryRepository>()
        every { categories.getVisibleCategories() } returns flowOf(
            income.map { CategoryEntity(name = it, color = "#000000", isIncome = true) } +
                expense.map { CategoryEntity(name = it, color = "#000000") }
        )
        val purchase = TransactionEntity(
            id = 1, amount = BigDecimal("6.75"), merchantName = "Zaman", category = "Others",
            transactionType = TransactionType.EXPENSE, dateTime = LocalDateTime.now(), currency = "JOD",
            transactionHash = "h"
        )

        assertEquals(listOf("Shopping", "Groceries"), SmsBroadcastReceiver.quickPicksFor(purchase, transactions, categories))
    }
}
