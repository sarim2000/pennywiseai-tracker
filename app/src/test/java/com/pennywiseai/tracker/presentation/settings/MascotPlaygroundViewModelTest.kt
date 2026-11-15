package com.pennywiseai.tracker.presentation.settings

import android.content.Context
import androidx.work.WorkManager
import com.pennywiseai.tracker.core.AppForegroundState
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.events.TransactionEvents
import com.pennywiseai.tracker.data.preferences.UserPreferences
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.presentation.chat.ChatTabViewModel
import com.pennywiseai.tracker.ui.components.mascot.reactionFor
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The playground's buttons play on the real Chat tab, through the app's own event source. */
class MascotPlaygroundViewModelTest {
    @Test
    fun `each button gives the Chat tab that type's reaction`() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        mockkObject(WorkManager.Companion)
        try {
            every { WorkManager.getInstance(any()) } returns mockk {
                every { getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())
            }
            val events = TransactionEvents(AppForegroundState { true })
            val playground = MascotPlaygroundViewModel(events)
            val tab = ChatTabViewModel(
                mockk<Context>(),
                events,
                AppForegroundState { true },
                mockk<UserPreferencesRepository> { every { userPreferences } returns flowOf(UserPreferences()) },
            )
            runTest(dispatcher) {
                runCurrent() // the tab is listening
                for (type in TransactionType.entries) {
                    playground.fire(type)
                    runCurrent()
                    assertEquals(reactionFor(type), tab.reactions.shown.value)
                    advanceUntilIdle() // the hold ends and the owl is back
                    assertNull(tab.reactions.shown.value)
                }
            }
        } finally {
            unmockkObject(WorkManager.Companion)
            Dispatchers.resetMain()
        }
    }
}
