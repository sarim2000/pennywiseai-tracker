package com.pennywiseai.tracker.data.database

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Selecting a category or "All Time" with 1,000+ transactions crashed Android 11 and
 * older: the split lookup bound one variable per transaction, past SQLite's 999 limit.
 */
@RunWith(AndroidJUnit4::class)
class SplitsForActiveTransactionsTest {
    private val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, PennyWiseDatabase::class.java
    ).setDriver(AndroidSQLiteDriver()).build()
    private val transactions = db.transactionDao()
    private val splits = db.transactionSplitDao()

    @After
    fun tearDown() = db.close()

    private fun tx(n: Int, deleted: Boolean = false) = TransactionEntity(
        amount = BigDecimal("10"), merchantName = "Synthetic Shop", category = "Food",
        transactionType = TransactionType.EXPENSE, dateTime = LocalDateTime.of(2026, 1, 1, 12, 0),
        transactionHash = "synthetic-$n", isDeleted = deleted
    )

    private fun split(transactionId: Long, category: String) =
        TransactionSplitEntity(transactionId = transactionId, category = category, amount = BigDecimal("5"))

    private suspend fun observedCategories() =
        splits.observeSplitsForActiveTransactions().first().map { it.category }.sorted()

    @Test
    fun readsSplitsWithMoreTransactionsThanTheOldBindLimit() = runBlocking {
        transactions.insertTransactions((1..1500).map { tx(it) })
        val all = transactions.getAllTransactions().first()
        assertEquals(1500, all.size)
        splits.insertSplits(listOf(split(all.first().id, "Groceries"), split(all.last().id, "Dining")))

        assertEquals(listOf("Dining", "Groceries"), observedCategories())
    }

    @Test
    fun leavesOutSplitsOfDeletedTransactions() = runBlocking {
        val kept = transactions.insertTransaction(tx(1))
        val deleted = transactions.insertTransaction(tx(2, deleted = true))
        splits.insertSplits(listOf(split(kept, "Groceries"), split(deleted, "Dining")))

        assertEquals(listOf("Groceries"), observedCategories())
    }

    /** One live collector throughout: a fresh read after each write would pass with no invalidation at all. */
    @Test
    fun reEmitsWhenASplitOrItsParentChanges() = runBlocking {
        val id = transactions.insertTransaction(tx(1))
        val emissions = Channel<List<String>>(Channel.UNLIMITED)
        val collector = launch {
            splits.observeSplitsForActiveTransactions().collect { rows -> emissions.send(rows.map { it.category }) }
        }

        withTimeout(10_000) {
            assertEquals(emptyList<String>(), emissions.receive())

            splits.insertSplit(split(id, "Groceries"))
            assertEquals(listOf("Groceries"), emissions.receive())

            transactions.softDeleteTransaction(id)
            assertEquals(emptyList<String>(), emissions.receive())
        }
        collector.cancel()
    }
}
