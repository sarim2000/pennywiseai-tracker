package com.pennywiseai.tracker.presentation.unrecognized

import com.pennywiseai.tracker.core.AppForegroundState
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity
import com.pennywiseai.tracker.data.events.MascotMoment
import com.pennywiseai.tracker.data.events.MascotMoments
import com.pennywiseai.tracker.data.manager.TaughtMessageBooker
import com.pennywiseai.tracker.data.preferences.TaughtTemplatesStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/** Teaching a message tells the Chat tab's mascot, but only when it booked something new. */
@OptIn(ExperimentalCoroutinesApi::class)
class UnrecognizedSmsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** A made-up message the sheet opens ready to save: amount, currency and direction read. */
    private val message = UnrecognizedSmsEntity(
        id = 7,
        sender = "AD-ZQTBNK",
        smsBody = "Purchased SAR 50.00 at Juice Bar card ending 1234. Balance SAR 900.00",
        receivedAt = LocalDateTime.of(2026, 1, 2, 10, 0),
    )

    private fun moments(outcome: TaughtMessageBooker.Outcome, teach: UnrecognizedSmsViewModel.() -> Unit): List<MascotMoment> {
        val booker = mockk<TaughtMessageBooker>(relaxed = true) {
            coEvery { bookMessage(any(), any(), any()) } returns outcome
            coEvery { bookWaiting(any()) } returns outcome
        }
        val templates = mockk<TaughtTemplatesStore>(relaxed = true) {
            every { forSender(any()) } returns emptyList()
        }
        val mascotMoments = MascotMoments(AppForegroundState { true })
        val heard = mutableListOf<MascotMoment>()
        runTest(dispatcher) {
            backgroundScope.launch { mascotMoments.moments.collect { heard += it } }
            val viewModel = UnrecognizedSmsViewModel(
                context = mockk(relaxed = true),
                unrecognizedSmsRepository = mockk(relaxed = true),
                learnedSendersStore = mockk(relaxed = true),
                trackedAppsStore = mockk(relaxed = true),
                taughtTemplatesStore = templates,
                taughtMessageBooker = booker,
                smsInboxReader = mockk(relaxed = true),
                mascotMoments = mascotMoments,
            )
            viewModel.teach()
        }
        return heard
    }

    private val saveTeach: UnrecognizedSmsViewModel.() -> Unit = {
        teachWaiting(message)
        assertEquals(null, teach.value?.missing)
        saveTeach()
    }

    @Test
    fun `teaching a message that books a transaction is a moment, once`() {
        assertEquals(listOf(MascotMoment.MessageTaught), moments(TaughtMessageBooker.Outcome.SAVED, saveTeach))
    }

    @Test
    fun `teaching a message already booked, ignored or that failed to book is not`() {
        assertEquals(emptyList<MascotMoment>(), moments(TaughtMessageBooker.Outcome.SETTLED, saveTeach))
        assertEquals(emptyList<MascotMoment>(), moments(TaughtMessageBooker.Outcome.FAILED, saveTeach))
    }

    @Test
    fun `adding a message a taught template already reads is a plain save, not a lesson`() {
        assertEquals(emptyList<MascotMoment>(), moments(TaughtMessageBooker.Outcome.SAVED) { addTaught(message) })
    }
}
