package com.pennywiseai.tracker.presentation.chat

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.pennywiseai.tracker.core.AppForegroundState
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.events.TransactionEvents
import com.pennywiseai.tracker.data.events.TransactionOrigin
import com.pennywiseai.tracker.data.preferences.UserPreferences
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.navigation.BottomNavItem
import com.pennywiseai.tracker.ui.components.mascot.SmsScanStatus
import com.pennywiseai.tracker.ui.components.mascot.reactionFor
import com.pennywiseai.tracker.worker.OptimizedSmsReaderWorker
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

/** How the Chat tab reads the inbox scan's work. */
class ChatTabViewModelTest {
    private fun work(state: WorkInfo.State, saved: Int = 0, vararg tags: String) = WorkInfo(
        id = UUID.randomUUID(),
        state = state,
        tags = tags.toSet(),
        outputData = workDataOf(OptimizedSmsReaderWorker.PROGRESS_SAVED to saved),
    )

    @Test
    fun `queued or running work is a running scan`() {
        assertEquals(SmsScanStatus.Running, smsScanStatus(listOf(work(WorkInfo.State.ENQUEUED))))
        assertEquals(SmsScanStatus.Running, smsScanStatus(listOf(work(WorkInfo.State.CANCELLED), work(WorkInfo.State.RUNNING))))
    }

    @Test
    fun `a finished scan reports its new rows, and a rebuild none`() {
        assertEquals(SmsScanStatus.Finished(3), smsScanStatus(listOf(work(WorkInfo.State.SUCCEEDED, saved = 3))))
        assertEquals(
            SmsScanStatus.Finished(0),
            smsScanStatus(listOf(work(WorkInfo.State.SUCCEEDED, 120, OptimizedSmsReaderWorker.TAG_REBUILD))),
        )
    }

    @Test
    fun `no work, or work that failed or was cancelled, is idle`() {
        assertEquals(SmsScanStatus.Idle, smsScanStatus(emptyList()))
        assertEquals(SmsScanStatus.Idle, smsScanStatus(listOf(work(WorkInfo.State.FAILED), work(WorkInfo.State.CANCELLED))))
    }

    private val events = TransactionEvents(AppForegroundState { true })
    private val backgrounded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val preferences = MutableStateFlow(UserPreferences())

    /** The ViewModel as the app builds it, with nothing on screen following its reactions. */
    private fun withViewModel(block: (ChatTabViewModel) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkObject(WorkManager.Companion)
        try {
            every { WorkManager.getInstance(any()) } returns mockk {
                every { getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())
            }
            val appForeground = object : AppForegroundState {
                override fun isInForeground() = true
                override val backgrounded: Flow<Unit> = this@ChatTabViewModelTest.backgrounded
            }
            val repository = mockk<UserPreferencesRepository> { every { userPreferences } returns preferences }
            block(ChatTabViewModel(mockk<Context>(), events, appForeground, repository))
        } finally {
            unmockkObject(WorkManager.Companion)
            Dispatchers.resetMain()
        }
    }

    private fun hideChat(hidden: Boolean) {
        preferences.value = UserPreferences(hiddenNavTabs = if (hidden) setOf(BottomNavItem.Chat.route) else emptySet())
    }

    @Test
    fun `a save made while the tab is off screen, app in the foreground, is waiting when it returns`() = withViewModel { viewModel ->
        // Add covers the tabs, so nothing on screen follows the reactions.
        events.onSaved(TransactionType.INCOME, TransactionOrigin.MANUAL)

        assertEquals(reactionFor(TransactionType.INCOME), viewModel.reactions.shown.value)
    }

    @Test
    fun `a save made while Chat is hidden shows nothing once it is back`() = withViewModel { viewModel ->
        hideChat(true)
        events.onSaved(TransactionType.INCOME, TransactionOrigin.MANUAL)
        hideChat(false)
        assertNull(viewModel.reactions.shown.value)

        // Back on the bar, it reacts again.
        events.onSaved(TransactionType.EXPENSE, TransactionOrigin.MANUAL)
        assertEquals(reactionFor(TransactionType.EXPENSE), viewModel.reactions.shown.value)
    }

    @Test
    fun `hiding Chat clears the reaction it was showing`() = withViewModel { viewModel ->
        events.onSaved(TransactionType.INCOME, TransactionOrigin.MANUAL)
        hideChat(true)
        assertNull(viewModel.reactions.shown.value)
    }

    @Test
    fun `leaving the app clears the reaction with no scan observer running`() = withViewModel { viewModel ->
        // Made on Add, which covers the tabs: no bar is up to follow the scan.
        events.onSaved(TransactionType.INCOME, TransactionOrigin.MANUAL)
        assertEquals(reactionFor(TransactionType.INCOME), viewModel.reactions.shown.value)

        backgrounded.tryEmit(Unit)

        assertNull(viewModel.reactions.shown.value)
    }
}
