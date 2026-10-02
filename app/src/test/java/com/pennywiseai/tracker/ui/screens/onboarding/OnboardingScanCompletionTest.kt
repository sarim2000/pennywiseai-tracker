package com.pennywiseai.tracker.ui.screens.onboarding

import androidx.work.Data
import com.pennywiseai.tracker.worker.OptimizedSmsReaderWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The first-scan handoff: by the time the worker reports SUCCEEDED, WorkManager
 * has cleared its progress, so the screen can only learn what was imported from
 * the worker's output. When that went missing, every new user was told "No
 * transactions found" straight after their inbox was imported.
 */
class OnboardingScanCompletionTest {

    @Test
    fun `the counts the worker returns reach the completion screen`() {
        val out = OptimizedSmsReaderWorker.completionData(total = 80, processed = 80, parsed = 71, saved = 69)

        val done = OnBoardingUiState().completedWith(out)

        assertEquals(69, done.scanSaved)
        assertEquals(80, done.scanTotal)
        assertTrue(done.scanCompleted)
    }

    @Test
    fun `missing output keeps the last running count instead of zero`() {
        val whileRunning = OnBoardingUiState(scanTotal = 80, scanSaved = 42)

        val done = whileRunning.completedWith(Data.EMPTY)

        assertEquals(42, done.scanSaved)
        assertEquals(80, done.scanTotal)
    }
}
