package com.pennywiseai.tracker.data.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.receiver.BankNotificationConfig
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
            row(6, "AD-KOTAKB", date = LocalDateTime.of(2025, 1, 1, 12, 0)) // older than the re-read window -> kept
        ).forEach { dao.insertTransaction(it) }

        dao.deleteRebuildableSmsTransactions(
            BankNotificationConfig.notificationAliases.toList(),
            from = LocalDateTime.of(2026, 7, 1, 0, 0)
        )

        val left = db.openHelper.readableDatabase.query("SELECT id FROM transactions ORDER BY id").use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }
        assertEquals(listOf(2L, 3L, 4L, 5L, 6L), left)
    }
}
