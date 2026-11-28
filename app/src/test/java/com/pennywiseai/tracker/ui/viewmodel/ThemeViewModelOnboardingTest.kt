package com.pennywiseai.tracker.ui.viewmodel

import com.pennywiseai.tracker.data.preferences.OnboardingProgress
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** The upgrade shortcut (SMS already granted) must not undo an onboarding resumed after a kill. */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelOnboardingTest {
    private val prefs = mockk<UserPreferencesRepository>(relaxed = true)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `an onboarding in progress is not marked complete`() {
        coEvery { prefs.getOnboardingProgress() } returns OnboardingProgress("SMS_SCAN", "A")
        ThemeViewModel(prefs).markOnboardingCompleted()
        coVerify(exactly = 0) { prefs.updateHasCompletedOnboarding(any(), any()) }
    }

    @Test
    fun `an upgrader with no saved step is marked complete`() {
        coEvery { prefs.getOnboardingProgress() } returns null
        ThemeViewModel(prefs).markOnboardingCompleted()
        coVerify(exactly = 1) { prefs.updateHasCompletedOnboarding(true, false) }
    }
}
