package com.pennywiseai.tracker.presentation.onboarding

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
}
