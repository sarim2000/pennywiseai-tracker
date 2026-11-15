package com.pennywiseai.tracker.presentation.chat

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.pennywiseai.tracker.core.AppForegroundState
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.events.TransactionEvents
import com.pennywiseai.tracker.data.events.TransactionOrigin
import com.pennywiseai.tracker.ui.components.mascot.SmsScanStatus
import com.pennywiseai.tracker.ui.components.mascot.reactionFor
import com.pennywiseai.tracker.worker.OptimizedSmsReaderWorker
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
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

    @Test
    fun `a save made while the tab is off screen, app in the foreground, is waiting when it returns`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkObject(WorkManager.Companion)
        try {
            every { WorkManager.getInstance(any()) } returns mockk {
                every { getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())
            }
            val events = TransactionEvents(AppForegroundState { true })
            val viewModel = ChatTabViewModel(mockk<Context>(), events)

            // Add covers the tabs, so nothing on screen follows the reactions.
            events.onSaved(TransactionType.INCOME, TransactionOrigin.MANUAL)

            assertEquals(reactionFor(TransactionType.INCOME), viewModel.reactions.shown.value)
        } finally {
            unmockkObject(WorkManager.Companion)
            Dispatchers.resetMain()
        }
    }
}
