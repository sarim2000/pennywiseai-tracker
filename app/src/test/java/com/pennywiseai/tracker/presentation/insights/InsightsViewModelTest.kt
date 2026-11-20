package com.pennywiseai.tracker.presentation.insights

import androidx.lifecycle.SavedStateHandle
import com.pennywiseai.tracker.domain.insights.InsightsLoader
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `insights are worked out off the main thread`() = runTest {
        val background = StandardTestDispatcher(testScheduler)
        var ranOn: CoroutineContext.Element? = null
        val loader = mockk<InsightsLoader>()
        every { loader.observe(any()) } returns flow {
            ranOn = currentCoroutineContext()[ContinuationInterceptor]
            awaitCancellation()
        }
        val viewModel = InsightsViewModel(loader, mockk(relaxed = true), SavedStateHandle(), background)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        advanceUntilIdle()

        assertSame(background, ranOn)
    }
}
