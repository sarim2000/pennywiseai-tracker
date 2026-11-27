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
}
