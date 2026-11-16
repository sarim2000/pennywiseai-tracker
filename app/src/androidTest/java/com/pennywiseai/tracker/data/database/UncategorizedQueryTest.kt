package com.pennywiseai.tracker.data.database

import androidx.room3.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.needsCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDateTime

/** The chat's "N uncategorized" counts in SQL what the picker's walk checks in Kotlin: the two must agree. */
@RunWith(AndroidJUnit4::class)
class UncategorizedQueryTest {
    private val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, PennyWiseDatabase::class.java
    ).build()

    @After fun close() = db.close()

    private fun row(id: Long, category: String, excluded: Boolean = false, deleted: Boolean = false) = TransactionEntity(
        id = id, amount = BigDecimal("10"), merchantName = "M", category = category,
        transactionType = TransactionType.EXPENSE, dateTime = LocalDateTime.of(2026, 10, 1, 12, 0).plusHours(id),
        transactionHash = "h$id", excludedFromAnalytics = excluded, isDeleted = deleted,
    )

    @Test fun matchesNeedsCategory() = runBlocking {
        val dao = db.transactionDao()
        val rows = listOf(
            row(1, ""),
            row(2, "   "),
            row(3, "Misc"), // "Others" as the user renamed it
            row(4, "Others"), // the old name: a real category now, or none
            row(5, "Food"),
            row(6, "Misc", excluded = true),
            row(7, "Misc", deleted = true),
            row(8, "Misc"), // split across categories: filed by its splits
        )
        rows.forEach { dao.insertTransaction(it) }
        db.transactionSplitDao().insertSplits(
            listOf(TransactionSplitEntity(transactionId = 8, category = "Food", amount = BigDecimal("4")),
                TransactionSplitEntity(transactionId = 8, category = "Transport", amount = BigDecimal("6")))
        )

        val counted = dao.getUncategorized("Misc").first().map { it.id }
        assertEquals(listOf(3L, 2L, 1L), counted) // newest first
        val split = db.transactionSplitDao().getSplitTransactionIds(rows.map { it.id }).toSet()
        assertEquals(setOf(8L), split)
        assertEquals(rows.filter { it.needsCategory("Misc", split = it.id in split) }.map { it.id }.sortedDescending(), counted)
    }
}
