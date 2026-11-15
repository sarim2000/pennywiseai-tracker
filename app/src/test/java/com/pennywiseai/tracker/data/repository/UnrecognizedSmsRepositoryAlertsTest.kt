package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.UnrecognizedSmsDao
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.LocalDateTime

/** A handled or deleted message takes its suggestion alert down, by the same row id. */
class UnrecognizedSmsRepositoryAlertsTest {

    private val dao = mockk<UnrecognizedSmsDao>()
    private val alerts = mockk<UnrecognizedSmsAlerts>(relaxed = true)
    private val repository = UnrecognizedSmsRepository(dao, alerts)

    @Test
    fun `deleteMessage cancels that row's alert`() = runBlocking {
        coJustRun { dao.softDeleteById(5L) }

        repository.deleteMessage(5L)

        verify(exactly = 1) { alerts.cancel(5L) }
    }

    @Test
    fun `deleteAll cancels every suggestion alert`() = runBlocking {
        coJustRun { dao.deleteAll() }

        repository.deleteAll()

        verify(exactly = 1) { alerts.cancelAll() }
    }

    @Test
    fun `settle cancels the alert of the row it hides`() = runBlocking {
        val receivedAt = LocalDateTime.of(2026, 1, 2, 10, 0)
        coEvery { dao.findBySenderAndBody("SENDER", "body") } returns
            UnrecognizedSmsEntity(id = 11L, sender = "SENDER", smsBody = "body", receivedAt = receivedAt)
        coJustRun { dao.softDeleteById(11L) }

        repository.settle("SENDER", "body", receivedAt)

        verify(exactly = 1) { alerts.cancel(11L) }
    }

    @Test
    fun `settle inserts a hidden row when there is none and cancels its alert`() = runBlocking {
        val receivedAt = LocalDateTime.of(2026, 1, 2, 10, 0)
        coEvery { dao.findBySenderAndBody("SENDER", "body") } returns null
        coEvery { dao.insert(any()) } returns 12L
        coJustRun { dao.softDeleteById(12L) }

        repository.settle("SENDER", "body", receivedAt)

        verify(exactly = 1) { alerts.cancel(12L) }
    }
}
