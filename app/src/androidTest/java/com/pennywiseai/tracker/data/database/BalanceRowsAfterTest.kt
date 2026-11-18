package com.pennywiseai.tracker.data.database

import androidx.room3.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDateTime

/** The real SQL behind "derived" vs anchor rows in the #982 shift. */
@RunWith(AndroidJUnit4::class)
class BalanceRowsAfterTest {
    private val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, PennyWiseDatabase::class.java
    ).build()

    @After fun close() = db.close()

    private fun at(h: Int) = LocalDateTime.of(2026, 1, 1, h, 0)

    private fun txn(id: Long, balanceAfter: String?) = TransactionEntity(
        id = id, amount = BigDecimal("10"), merchantName = "M$id", category = "Others",
        transactionType = TransactionType.EXPENSE, dateTime = at(id.toInt()), transactionHash = "h$id",
        balanceAfter = balanceAfter?.let(::BigDecimal)
    )

    private fun row(h: Int, txnId: Long?, sms: String?, source: String?) = AccountBalanceEntity(
        bankName = "Test Bank", accountLast4 = "1111", balance = BigDecimal("100"), timestamp = at(h),
        transactionId = txnId, smsSource = sms, sourceType = source, creditLimit = BigDecimal("900")
    )

    @Test fun marksDerivedRowsAndAnchors() = runBlocking {
        listOf(txn(1, null), txn(2, "500"), txn(3, null)).forEach { db.transactionDao().insertTransaction(it) }
        val dao = db.accountBalanceDao()
        listOf(
            row(1, 1, "sms", "TRANSACTION"), // balance-less SMS -> derived
            row(2, 2, "sms", "TRANSACTION"), // stated balance -> anchor
            row(3, 3, null, "TRANSACTION"),  // delta row -> derived, but an anchor if it predates the rebuild
            row(4, null, null, null),        // balance notice -> anchor
            row(5, null, null, "MANUAL"),    // user set -> anchor
            row(6, 99, "sms", "TRANSACTION") // its transaction is gone -> anchor
        ).forEach { dao.insertBalance(it) }

        val normal = dao.getBalanceRowsAfter("Test Bank", "1111", at(0), frozenBefore = null)
        assertEquals(listOf(true, false, true, false, false, false), normal.map { it.derived })
        assertEquals(0, BigDecimal("900").compareTo(normal.first().creditLimit))

        val rebuild = dao.getBalanceRowsAfter("Test Bank", "1111", at(0), frozenBefore = java.time.LocalDateTime.now().plusMinutes(1))
        assertEquals(listOf(true, false, false, false, false, false), rebuild.map { it.derived })

        // A delta row created after the rebuild began is still derived.
        val midRebuild = dao.getBalanceRowsAfter("Test Bank", "1111", at(0), frozenBefore = java.time.LocalDateTime.now().minusMinutes(1))
        assertEquals(listOf(true, false, true, false, false, false), midRebuild.map { it.derived })
    }

    /** Only a figure the bank reported counts as already holding older transactions (#1022). */
    @Test fun bankBalanceIgnoresWorkedOutAndUserRows() = runBlocking {
        listOf(txn(1, null), txn(2, "500")).forEach { db.transactionDao().insertTransaction(it) }
        val dao = db.accountBalanceDao()
        listOf(
            row(1, 2, "sms", "TRANSACTION"),  // stated balance -> the bank's
            row(2, 1, "sms", "TRANSACTION"),  // balance-less SMS -> worked out
            row(3, null, null, "TRANSACTION"), // delta row -> worked out
            row(4, null, null, "MANUAL"),      // user set -> not the bank's
            row(5, null, null, "OPENING")
        ).forEach { dao.insertBalance(it) }
        assertTrue(dao.hasBankBalanceAtOrAfter("Test Bank", "1111", at(1)))
        assertFalse(dao.hasBankBalanceAtOrAfter("Test Bank", "1111", at(2)))

        dao.insertBalance(row(6, null, null, null)) // balance notice
        assertTrue(dao.hasBankBalanceAtOrAfter("Test Bank", "1111", at(6)))
        assertFalse(dao.hasBankBalanceAtOrAfter("Test Bank", "1111", at(7)))
    }
}
