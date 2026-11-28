package com.pennywiseai.tracker.presentation.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Onboarding is one back-stack entry, so system Back only returns a step while
 * [OnBoardingUiState.canGoBack] holds; otherwise it leaves the app. Pressing it
 * on the name step used to close the app.
 */
class OnboardingBackTest {

    @Test
    fun `every step after the first goes back a step`() {
        OnBoardingStep.entries.filter { it != OnBoardingStep.WELCOME }.forEach { step ->
            assertTrue(step.name, OnBoardingUiState(currentStep = step).canGoBack)
        }
    }

    @Test
    fun `the first step lets Back leave the app`() {
        assertFalse(OnBoardingUiState(currentStep = OnBoardingStep.WELCOME).canGoBack)
    }

    @Test
    fun `a running scan does not step back`() {
        assertFalse(OnBoardingUiState(currentStep = OnBoardingStep.SMS_SCAN, isScanning = true).canGoBack)
    }

    @Test
    fun `a saved step reopens there`() {
        assertEquals(OnBoardingStep.ACCOUNT_SETUP, resumeStep("ACCOUNT_SETUP", smsGranted = true))
        assertEquals(OnBoardingStep.PROFILE, resumeStep("PROFILE", smsGranted = false))
    }

    @Test
    fun `the scan steps need the SMS permission, else the user is asked again`() {
        assertEquals(OnBoardingStep.SMS_SCAN, resumeStep("SMS_SCAN", smsGranted = true))
        assertEquals(OnBoardingStep.PERMISSIONS, resumeStep("SMS_SCAN", smsGranted = false))
    }

    @Test
    fun `an unknown saved step starts at Welcome`() {
        assertEquals(OnBoardingStep.WELCOME, resumeStep("GONE", smsGranted = true))
    }

    @Test
    fun `a scan that added nothing new still reports what is stored`() {
        val rescan = OnBoardingUiState(currentStep = OnBoardingStep.SMS_SCAN, scanCompleted = true, scanTotal = 80)

        val shown = rescan.withStoredTransactions(stored = 69)

        assertEquals(69, shown.scanSaved)
        assertTrue(shown.scanCompleted)
    }

    @Test
    fun `an empty store stays none found and a running scan is left alone`() {
        val done = OnBoardingUiState(scanCompleted = true)
        assertEquals(0, done.withStoredTransactions(stored = 0).scanSaved)
        val running = OnBoardingUiState(isScanning = true)
        assertFalse(running.withStoredTransactions(stored = 5).scanCompleted)
    }

    @Test
    fun `a finished run that inserted fewer than are stored shows the stored count`() {
        val resumed = OnBoardingUiState(scanCompleted = true, scanTotal = 4956, scanSaved = 2116)

        val shown = resumed.withStoredTransactions(stored = 2994)

        assertEquals(2994, shown.scanSaved)
        assertEquals(4956, shown.scanTotal)
    }

    @Test
    fun `a scan still pending is not shown as completed`() {
        val pending = OnBoardingUiState(isScanning = true, scanCompleted = false)

        val shown = pending.withStoredTransactions(stored = 878)

        assertFalse(shown.scanCompleted)
        assertEquals(0, shown.scanSaved)
    }

    @Test
    fun `saved choices come back, so Finish saves the picked currency and not the default`() {
        val colors = listOf(10, 20, 30)
        val chosen = OnBoardingUiState(
            currentStep = OnBoardingStep.PERMISSIONS, userName = " Sam ", selectedAvatarIndex = 2,
            selectedBackgroundColor = 1, selectedCurrency = "AUD", currencyPicked = true
        )

        val saved = chosen.toProgress(colors)
        val restored = OnBoardingUiState(selectedCurrency = "USD")
            .restoredFrom(saved, OnBoardingStep.PERMISSIONS, smsGranted = false, backgroundColors = colors)

        assertEquals("AUD", restored.selectedCurrency)
        assertTrue(restored.currencyPicked)
        assertEquals("Sam", restored.userName)
        assertEquals(2, restored.selectedAvatarIndex)
        assertEquals(1, restored.selectedBackgroundColor)
    }

    @Test
    fun `an unpicked currency guess is not saved and stays the fresh guess`() {
        val saved = OnBoardingUiState(selectedCurrency = "AUD").toProgress(listOf(10))

        assertEquals(null, saved.pickedCurrency)
        val restored = OnBoardingUiState(selectedCurrency = "USD")
            .restoredFrom(saved, OnBoardingStep.PROFILE, smsGranted = false, backgroundColors = listOf(10))
        assertEquals("USD", restored.selectedCurrency)
        assertFalse(restored.currencyPicked)
    }
}
