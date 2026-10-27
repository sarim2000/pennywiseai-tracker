package com.pennywiseai.tracker.data.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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

    @Test fun marksDerivedRowsAndAnchors() = runBlocking {
        fun txn(id: Long, balanceAfter: String?) = TransactionEntity(
            id = id, amount = BigDecimal("10"), merchantName = "M$id", category = "Others",
            transactionType = TransactionType.EXPENSE, dateTime = at(id.toInt()), transactionHash = "h$id",
            balanceAfter = balanceAfter?.let(::BigDecimal)
        )
        listOf(txn(1, null), txn(2, "500"), txn(3, null)).forEach { db.transactionDao().insertTransaction(it) }

        fun row(h: Int, txnId: Long?, sms: String?, source: String?) = AccountBalanceEntity(
            bankName = "Test Bank", accountLast4 = "1111", balance = BigDecimal("100"), timestamp = at(h),
            transactionId = txnId, smsSource = sms, sourceType = source, creditLimit = BigDecimal("900")
        )
        val dao = db.accountBalanceDao()
        listOf(
            row(1, 1, "sms", "TRANSACTION"), // balance-less SMS -> derived
            row(2, 2, "sms", "TRANSACTION"), // stated balance -> anchor
            row(3, 3, null, "TRANSACTION"),  // delta row -> derived, but an anchor while rebuilding
            row(4, null, null, null),        // balance notice -> anchor
            row(5, null, null, "MANUAL"),    // user set -> anchor
            row(6, 99, "sms", "TRANSACTION") // its transaction is gone -> anchor
        ).forEach { dao.insertBalance(it) }

        val normal = dao.getBalanceRowsAfter("Test Bank", "1111", at(0), rebuilding = false)
        assertEquals(listOf(true, false, true, false, false, false), normal.map { it.derived })
        assertEquals(0, BigDecimal("900").compareTo(normal.first().creditLimit))

        val rebuild = dao.getBalanceRowsAfter("Test Bank", "1111", at(0), rebuilding = true)
        assertEquals(listOf(true, false, false, false, false, false), rebuild.map { it.derived })
    }
}
