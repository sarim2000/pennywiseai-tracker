package com.pennywiseai.tracker.ui

import com.pennywiseai.tracker.core.AppForegroundState
import com.pennywiseai.tracker.data.events.MascotMoment
import com.pennywiseai.tracker.data.events.MascotMoments
import com.pennywiseai.tracker.data.preferences.PrivacyPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** A shake that hides or shows amounts tells the Chat tab's owl, once per shake. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShakeToHideViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Half a second of hard shaking, a sample every 20 ms, from [startMs]. */
    private fun ShakeToHideViewModel.shake(startMs: Long) {
        for (t in 0..500L step 20) onSample(0f, 0f, 22f, (BOOT_MS + startMs + t) * 1_000_000L)
    }

    @Test
    fun `each shake announces what it left, hidden then shown`() = runTest(dispatcher) {
        var saved = false
        val preferences = mockk<PrivacyPreferences> {
            every { shakeToHideAmounts } returns flowOf(true)
            coEvery { toggleBalanceHidden(false) } answers { saved = !saved; saved }
            coEvery { claimShakeToHideHint() } returns false
        }
        val moments = MascotMoments(AppForegroundState { true })
        val heard = mutableListOf<MascotMoment>()
        backgroundScope.launch { moments.moments.collect { heard += it } }
        val viewModel = ShakeToHideViewModel(preferences, moments)

        viewModel.shake(startMs = 0)
        assertEquals(listOf(MascotMoment.AmountsHidden), heard)
        // A quiet couple of seconds, then the next shake shows them again.
        viewModel.shake(startMs = 3_000)
        assertEquals(listOf(MascotMoment.AmountsHidden, MascotMoment.AmountsShown), heard)
    }

    @Test
    fun `a sample that isn't a shake announces nothing`() = runTest(dispatcher) {
        val preferences = mockk<PrivacyPreferences> { every { shakeToHideAmounts } returns flowOf(true) }
        val moments = MascotMoments(AppForegroundState { true })
        val heard = mutableListOf<MascotMoment>()
        backgroundScope.launch { moments.moments.collect { heard += it } }
        val viewModel = ShakeToHideViewModel(preferences, moments)

        for (t in 0..1_000L step 20) viewModel.onSample(0f, 0f, 9.81f, (BOOT_MS + t) * 1_000_000L)
        assertEquals(emptyList<MascotMoment>(), heard)
    }

    private companion object {
        /** A realistic boot-clock time, not 0. */
        const val BOOT_MS = 5_000_000L
    }
}
