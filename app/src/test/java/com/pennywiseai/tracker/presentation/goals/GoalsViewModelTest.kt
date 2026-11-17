package com.pennywiseai.tracker.presentation.goals

import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.data.repository.GoalRepository
import com.pennywiseai.tracker.domain.goals.GoalAffordabilityLoader
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModelTest {
    private val goals = mockk<GoalRepository>()
    private val loader = mockk<GoalAffordabilityLoader>()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { goals.observeActiveGoals(any()) } returns flowOf(emptyList())
        every { goals.observeArchivedGoals(any()) } returns flowOf(emptyList())
        every { loader.observe(any()) } returns flowOf(emptyList())
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a new month weighs goals against the new month's history`() = runTest {
        val viewModel = GoalsViewModel(goals, mockk<EntitlementGate>(), loader)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.affordability.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.activeGoals.collect {} }

        val nextMonth = LocalDate.of(2026, 12, 1)
        viewModel.refreshDate(nextMonth)

        verify { loader.observe(nextMonth) }
        verify { goals.observeActiveGoals(nextMonth) }
    }
}
