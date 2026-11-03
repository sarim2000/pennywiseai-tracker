package com.pennywiseai.tracker.data.database

import androidx.room3.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDateTime

/** The alert's "Not an expense" and its Undo flip only the flag, and say when nothing changed. */
@RunWith(AndroidJUnit4::class)
class ExcludeFlagTest {
    private val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, PennyWiseDatabase::class.java
    ).build()

    @After fun close() = db.close()

    @Test fun excludeRepeatAndUndo() = runBlocking {
        val dao = db.transactionDao()
        val row = TransactionEntity(
            id = 1, amount = BigDecimal("100"), merchantName = "M", category = "Food",
            transactionType = TransactionType.EXPENSE, dateTime = LocalDateTime.of(2026, 10, 1, 12, 0),
            transactionHash = "h1", balanceAfter = BigDecimal("900")
        )
        dao.insertTransaction(row)
        val now = LocalDateTime.of(2026, 10, 2, 9, 0)

        assertEquals(1, dao.setExcludedFromAnalytics(1, true, now))
        assertEquals(0, dao.setExcludedFromAnalytics(1, true, now)) // repeat tap: no-op
        val excluded = dao.getTransactionById(1)!!
        assertEquals(true, excluded.excludedFromAnalytics)
        assertEquals(row.copy(excludedFromAnalytics = true, updatedAt = now), excluded.copy(createdAt = row.createdAt))

        assertEquals(1, dao.setExcludedFromAnalytics(1, false, now)) // Undo
        assertEquals(false, dao.getTransactionById(1)!!.excludedFromAnalytics)
    }
}
