package com.pennywiseai.tracker.domain.security

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLockDecisionTest {
    @Test fun `no screen lock and no biometrics lets the user in`() =
        assertEquals(AppLockDecision.LetIn, appLockDecision(canPrompt = false, deviceSecure = false))

    @Test fun `a screen lock always prompts, even when biometrics are unavailable`() =
        assertEquals(AppLockDecision.Prompt, appLockDecision(canPrompt = false, deviceSecure = true))

    @Test fun `available authentication prompts`() {
        assertEquals(AppLockDecision.Prompt, appLockDecision(canPrompt = true, deviceSecure = true))
        assertEquals(AppLockDecision.Prompt, appLockDecision(canPrompt = true, deviceSecure = false))
    }

    @Test fun `API 28 and 29 pair the PIN with weak biometrics`() {
        assertEquals(BIOMETRIC_WEAK or DEVICE_CREDENTIAL, appLockAuthenticators(28))
        assertEquals(BIOMETRIC_WEAK or DEVICE_CREDENTIAL, appLockAuthenticators(29))
    }

    @Test fun `other APIs pair the PIN with strong biometrics`() {
        assertEquals(BIOMETRIC_STRONG or DEVICE_CREDENTIAL, appLockAuthenticators(26))
        assertEquals(BIOMETRIC_STRONG or DEVICE_CREDENTIAL, appLockAuthenticators(27))
        assertEquals(BIOMETRIC_STRONG or DEVICE_CREDENTIAL, appLockAuthenticators(30))
        assertEquals(BIOMETRIC_STRONG or DEVICE_CREDENTIAL, appLockAuthenticators(35))
    }
}
