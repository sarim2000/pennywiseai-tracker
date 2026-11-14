package com.pennywiseai.tracker.data.notification

import android.app.Notification
import com.pennywiseai.tracker.data.database.dao.UnrecognizedSmsDao
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity
import com.pennywiseai.tracker.data.repository.UnrecognizedSmsAlerts
import com.pennywiseai.tracker.data.repository.UnrecognizedSmsRepository
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** #994 step 4: which notification posts a tracked app can turn into suggestions. */
class BankNotificationSkipTest {

    @Test
    fun `summaries are skipped, ongoing only for tracked apps`() {
        assertTrue(BankNotificationConfig.skipsPost(Notification.FLAG_GROUP_SUMMARY, trackedApp = false))
        assertTrue(BankNotificationConfig.skipsPost(Notification.FLAG_ONGOING_EVENT, trackedApp = true))
        assertFalse(BankNotificationConfig.skipsPost(Notification.FLAG_ONGOING_EVENT, trackedApp = false))
        assertFalse(BankNotificationConfig.skipsPost(0, trackedApp = true))
    }

    private val dao = mockk<UnrecognizedSmsDao>()
    private val repository = UnrecognizedSmsRepository(dao, mockk<UnrecognizedSmsAlerts>(relaxed = true))
    private val first = LocalDateTime.of(2026, 1, 2, 10, 0)
    private fun row(at: LocalDateTime) = UnrecognizedSmsEntity(sender = "com.example.pay", smsBody = "Spent AED 45.00 at Carrefour", receivedAt = at)

    @Test
    fun `a repost of the same text soon after is dropped`() = runBlocking {
        coEvery { dao.insert(any()) } returns -1
        coEvery { dao.findBySenderAndBody(any(), any()) } returns row(first).copy(id = 7, isDeleted = true)

        assertEquals(-1L, repository.insertOrRevive(row(first.plusSeconds(30))))
    }

    @Test
    fun `the same text later, after the first was handled, is offered again`() = runBlocking {
        coEvery { dao.insert(any()) } returns -1
        coEvery { dao.findBySenderAndBody(any(), any()) } returns row(first).copy(id = 7, isDeleted = true)
        coJustRun { dao.revive(7, any()) }

        assertEquals(7L, repository.insertOrRevive(row(first.plusHours(1))))
    }
}
