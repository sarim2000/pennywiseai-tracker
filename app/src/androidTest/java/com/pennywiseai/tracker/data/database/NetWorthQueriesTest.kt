package com.pennywiseai.tracker.data.database

import androidx.room3.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDateTime

/** The real SQL behind net worth's "where the balance comes from" and its trend. */
@RunWith(AndroidJUnit4::class)
class NetWorthQueriesTest {
    private val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, PennyWiseDatabase::class.java
    ).build()

    @After fun close() = db.close()

    private fun at(h: Int) = LocalDateTime.of(2026, 1, 1, h, 0)

    private fun row(last4: String, h: Int, txnId: Long?, sms: String?, source: String?) = AccountBalanceEntity(
        bankName = "Test Bank", accountLast4 = last4, balance = BigDecimal("100"), timestamp = at(h),
        transactionId = txnId, smsSource = sms, sourceType = source,
    )

    @Test fun evidenceSeparatesTheBanksFigureFromEverythingElse() = runBlocking {
        fun txn(id: Long, balanceAfter: String?) = TransactionEntity(
            id = id, amount = BigDecimal("10"), merchantName = "M$id", category = "Others",
            transactionType = TransactionType.EXPENSE, dateTime = at(id.toInt()), transactionHash = "h$id",
            balanceAfter = balanceAfter?.let(::BigDecimal), bankName = "Test Bank", accountNumber = "1111"
        )
        listOf(txn(1, null), txn(2, "500"), txn(3, null)).forEach { db.transactionDao().insertTransaction(it) }
        val dao = db.accountBalanceDao()
        listOf(
            row("1111", 0, null, null, "OPENING"),     // never counts
            row("1111", 1, 1, "sms", "TRANSACTION"),   // no balance stated
            row("1111", 2, 2, "sms", "TRANSACTION"),   // stated by the bank
            row("1111", 3, 3, null, "TRANSACTION"),    // a delta
            row("2222", 4, null, null, null),          // a balance notice
            row("2222", 5, null, null, "MANUAL"),      // set by the user after it
            row("3333", 6, 1, "sms", "TRANSACTION"),   // never pinned
        ).forEach { dao.insertBalance(it) }

        val evidence = dao.getBalanceEvidence().first().associateBy { it.accountLast4 }
        assertEquals(at(2), evidence.getValue("1111").reportedAt)
        assertEquals(at(2), evidence.getValue("1111").fixedAt)
        assertEquals(at(4), evidence.getValue("2222").reportedAt)
        assertEquals(at(5), evidence.getValue("2222").fixedAt)
        assertNull(evidence.getValue("3333").reportedAt)
        assertNull(evidence.getValue("3333").fixedAt)

        // What moved 1111 after its stated balance at 02:00, plus a transfer out of it.
        db.transactionDao().insertTransaction(
            TransactionEntity(
                amount = BigDecimal("5"), merchantName = "Move", category = "Others",
                transactionType = TransactionType.TRANSFER, dateTime = at(4), transactionHash = "t1",
                fromAccount = "1111", fromBankName = "Test Bank", toAccount = "4444",
            )
        )
        assertEquals(2, db.transactionDao().countTransactionsOnAccountSince("Test Bank", "1111", at(2)))
    }

    @Test fun aMarkedTransferCountsOnceOnEachAccount() = runBlocking {
        val dao = db.transactionDao()
        fun transfer(hash: String, bank: String, account: String, pairId: String?) = TransactionEntity(
            amount = BigDecimal("50"), merchantName = "Move", category = "Others",
            transactionType = TransactionType.TRANSFER, dateTime = at(3), transactionHash = hash,
            bankName = bank, accountNumber = account, transferPairId = pairId,
            fromAccount = "1111", fromBankName = "Test Bank", toAccount = "2222", toBankName = "Other Bank",
        )
        // A marked pair: the debit on 1111 and the credit on 2222, each with the whole route.
        dao.insertTransaction(transfer("p1", "Test Bank", "1111", "pair"))
        dao.insertTransaction(transfer("p2", "Other Bank", "2222", "pair"))
        // A standalone transfer, stored once, moves both accounts.
        dao.insertTransaction(transfer("s1", "Cash", "0000", null))

        assertEquals(2, dao.countTransactionsOnAccountSince("Test Bank", "1111", at(1)))
        assertEquals(2, dao.countTransactionsOnAccountSince("Other Bank", "2222", at(1)))
    }

    @Test fun trendRowsSeedEachAccountWithItsLastRowBeforeTheStart() = runBlocking {
        val dao = db.accountBalanceDao()
        listOf(
            row("1111", 0, null, null, "OPENING"),
            row("1111", 1, null, null, "MANUAL"),
            row("1111", 2, null, null, "MANUAL"),
            row("1111", 5, null, null, "MANUAL"),
            row("2222", 3, null, null, null),
        ).forEach { dao.insertBalance(it) }

        val rows = dao.getTrendRows(at(4)).first().map { it.accountLast4 to it.timestamp.hour }.toSet()
        assertEquals(setOf("1111" to 2, "1111" to 5, "2222" to 3), rows)
    }
}
