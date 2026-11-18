package com.pennywiseai.tracker.data.database

import androidx.room3.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.notification.BankNotificationConfig
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDateTime

/** "Rebuild from scratch" deletes only rows the SMS inbox can bring back. */
@RunWith(AndroidJUnit4::class)
class RebuildDeleteTest {
    private val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, PennyWiseDatabase::class.java
    ).build()

    @After fun close() = db.close()

    private fun row(id: Long, sender: String?, deleted: Boolean = false, date: LocalDateTime = LocalDateTime.of(2026, 10, 1, 12, 0)) = TransactionEntity(
        id = id, amount = BigDecimal("100"), merchantName = "M$id", category = "Others",
        transactionType = TransactionType.EXPENSE, dateTime = date,
        transactionHash = "h$id", smsSender = sender, smsBody = sender?.let { "body $id" }, isDeleted = deleted
    )

    @Test fun deletesOnlyActiveSmsRows() = runBlocking {
        val dao = db.transactionDao()
        val notification = BankNotificationConfig.notificationAliases.first()
        listOf(
            row(1, "AD-KOTAKB"),             // SMS: rebuilt from the inbox -> deleted
            row(2, null),                    // manual / chat / CSV -> kept
            row(3, "ICICI PDF"),             // PDF import -> kept
            row(4, notification),            // bank-app notification -> kept
            row(5, "AD-KOTAKB", deleted = true), // user deleted it -> kept, so it isn't resurrected
            row(6, "AD-KOTAKB", date = LocalDateTime.of(2025, 1, 1, 12, 0)), // older than the re-read window -> kept
            // Booked from a taught template: only comes back if the user confirms it again -> kept
            row(7, "AD-KOTAKB").copy(transactionHash = "taught_h7")
        ).forEach { dao.insertTransaction(it) }

        dao.deleteRebuildableSmsTransactions(
            BankNotificationConfig.notificationAliases.toList(),
            from = LocalDateTime.of(2026, 7, 1, 0, 0)
        )

        val left = db.longs("SELECT id FROM transactions ORDER BY id")
        assertEquals(listOf(2L, 3L, 4L, 5L, 6L, 7L), left)
    }

    @Test fun aRebuiltCardGetsItsBillBack() = runBlocking {
        val dao = db.accountBalanceDao()
        val before = AccountBalanceEntity(
            bankName = "Example Bank", accountLast4 = "0000", balance = BigDecimal("500"),
            timestamp = LocalDateTime.of(2026, 10, 1, 12, 0), isCreditCard = true,
            statementDay = 12, cardDueDay = 2, cardBillAmount = BigDecimal("450"),
            cardBillAsOf = LocalDateTime.of(2026, 10, 13, 9, 0),
        )
        dao.insertBalance(before)
        dao.deleteRebuildableBalances()
        // The re-read SMS writes a row knowing nothing of the bill.
        dao.insertBalance(before.copy(balance = BigDecimal("520"), statementDay = null, cardDueDay = null,
            cardBillAmount = null, cardBillAsOf = null, timestamp = LocalDateTime.of(2026, 10, 2, 12, 0)))

        dao.fillMissingAccountFields(
            before.bankName, before.accountLast4, before.statementDay, before.cardDueDay,
            before.cardBillAmount, before.cardBillAsOf, before.currency,
        )

        val rebuilt = dao.getLatestBalance("Example Bank", "0000")!!
        assertEquals(0, BigDecimal("520").compareTo(rebuilt.balance))
        assertEquals(12, rebuilt.statementDay)
        assertEquals(2, rebuilt.cardDueDay)
        assertEquals(0, BigDecimal("450").compareTo(rebuilt.cardBillAmount))
        assertEquals(before.cardBillAsOf, rebuilt.cardBillAsOf)
    }

    @Test fun aBillDoesNotCrossToAnotherCurrency() = runBlocking {
        val dao = db.accountBalanceDao()
        val target = AccountBalanceEntity(
            bankName = "Example Bank", accountLast4 = "1111", balance = BigDecimal("500"),
            timestamp = LocalDateTime.of(2026, 10, 1, 12, 0), isCreditCard = true, currency = "USD",
        )
        dao.insertBalance(target)

        // A merged INR card's bill: the due and statement day carry over, the amount doesn't.
        dao.fillMissingAccountFields(
            target.bankName, target.accountLast4, 12, 2,
            BigDecimal("4000"), LocalDateTime.of(2026, 10, 13, 9, 0), "INR",
        )

        val merged = dao.getLatestBalance("Example Bank", "1111")!!
        assertEquals(12, merged.statementDay)
        assertEquals(2, merged.cardDueDay)
        assertNull(merged.cardBillAmount)
        assertNull(merged.cardBillAsOf)
    }
}
