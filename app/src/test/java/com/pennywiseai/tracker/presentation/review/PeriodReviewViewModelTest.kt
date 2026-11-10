package com.pennywiseai.tracker.presentation.review

import androidx.lifecycle.SavedStateHandle
import com.pennywiseai.tracker.domain.review.PeriodReviewLoader
import com.pennywiseai.tracker.domain.review.ReviewKind
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PeriodReviewViewModelTest {
    private val loader = mockk<PeriodReviewLoader>()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { loader.observe(any(), any(), any()) } returns emptyFlow()
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a restored period stays the one it was, whatever the date is now`() = runTest {
        val chosenOn = LocalDate.of(2026, 1, 15)
        val state = SavedStateHandle(
            mapOf("selectedKind" to "WEEK", "selectedOffset" to -3, "selectedToday" to chosenOn.toEpochDay())
        )
        val viewModel = PeriodReviewViewModel(state, loader)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.review.collect {} }

        assertEquals(ReviewSelection(ReviewKind.WEEK, -3, chosenOn), viewModel.selection.value)
        verify { loader.observe(ReviewKind.WEEK, -3, chosenOn) }
    }

    @Test
    fun `moving between periods keeps counting from the same day and saves it`() {
        val chosenOn = LocalDate.of(2026, 1, 15)
        val state = SavedStateHandle(
            mapOf("selectedKind" to "WEEK", "selectedOffset" to -3, "selectedToday" to chosenOn.toEpochDay())
        )
        val viewModel = PeriodReviewViewModel(state, loader)

        viewModel.older()

        assertEquals(ReviewSelection(ReviewKind.WEEK, -4, chosenOn), viewModel.selection.value)
        assertEquals(chosenOn.toEpochDay(), state.get<Long>("selectedToday"))
    }
}
