package com.pennywiseai.tracker.domain.security

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.pennywiseai.tracker.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for handling biometric authentication using BiometricPrompt API.
 * Supports fingerprint, face recognition, and device credentials (PIN/password/pattern).
 */
@Singleton
class BiometricAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Check if biometric authentication can be used on this device
     */
    fun canAuthenticate(): BiometricCapability {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(appLockAuthenticators())) {
            BiometricManager.BIOMETRIC_SUCCESS ->
                BiometricCapability.Available

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                BiometricCapability.NoHardware

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                BiometricCapability.HardwareUnavailable

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                BiometricCapability.NoneEnrolled

            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                BiometricCapability.SecurityUpdateRequired

            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED ->
                BiometricCapability.Unsupported

            BiometricManager.BIOMETRIC_STATUS_UNKNOWN ->
                BiometricCapability.Unknown

            else -> BiometricCapability.Unknown
        }
    }

    /**
     * Check if biometric authentication is available and ready to use
     */
    fun isBiometricAvailable(): Boolean {
        return canAuthenticate() == BiometricCapability.Available
    }

    /** A PIN, pattern or password is set on the device. */
    private fun isDeviceSecure(): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /**
     * Create and show biometric prompt for authentication
     *
     * @param activity FragmentActivity required for BiometricPrompt
     * @param title Title shown in the prompt
     * @param subtitle Optional subtitle
     * @param description Optional description
     * @param onSuccess Callback when authentication succeeds
     * @param onError Callback when authentication fails with error message
     * @param onFailed Callback when authentication fails (e.g., wrong fingerprint)
     * @param onNoScreenLock Called instead of prompting when the device has no screen
     *   lock and no biometrics, so there is nothing to check; defaults to [onSuccess].
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = activity.getString(R.string.applock_prompt_title),
        subtitle: String = activity.getString(R.string.applock_prompt_subtitle),
        description: String = activity.getString(R.string.applock_prompt_description),
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit = {},
        onCancel: () -> Unit = {},
        onNoScreenLock: () -> Unit = onSuccess
    ) {
        if (appLockDecision(isBiometricAvailable(), isDeviceSecure()) == AppLockDecision.LetIn) {
            onNoScreenLock()
            return
        }
        val executor = ContextCompat.getMainExecutor(context)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription(description)
            .setAllowedAuthenticators(appLockAuthenticators())
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Don't treat user cancellation as an error
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        onError(errString.toString())
                    } else {
                        onCancel()
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailed()
                }
            })

        biometricPrompt.authenticate(promptInfo)
    }
}

/**
 * Biometrics with the device PIN/pattern/password as fallback. androidx.biometric
 * rejects BIOMETRIC_STRONG | DEVICE_CREDENTIAL on API 28–29, where
 * BIOMETRIC_WEAK | DEVICE_CREDENTIAL is the supported pairing.
 */
internal fun appLockAuthenticators(sdkInt: Int = Build.VERSION.SDK_INT): Int =
    if (sdkInt == Build.VERSION_CODES.P || sdkInt == Build.VERSION_CODES.Q) BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    else BIOMETRIC_STRONG or DEVICE_CREDENTIAL

enum class AppLockDecision { Prompt, LetIn }

/**
 * Prompt whenever the device can check anything. Only a device with no screen lock
 * and no biometrics lets the user straight in: there is nothing to check, and
 * refusing would lock them out of their own data.
 */
internal fun appLockDecision(canPrompt: Boolean, deviceSecure: Boolean): AppLockDecision =
    if (canPrompt || deviceSecure) AppLockDecision.Prompt else AppLockDecision.LetIn

/**
 * Represents the capability of the device to perform biometric authentication
 */
sealed class BiometricCapability {
    object Available : BiometricCapability()
    object NoHardware : BiometricCapability()
    object HardwareUnavailable : BiometricCapability()
    object NoneEnrolled : BiometricCapability()
    object SecurityUpdateRequired : BiometricCapability()
    object Unsupported : BiometricCapability()
    object Unknown : BiometricCapability()

    /** User-facing explanation, or null when authentication is available. */
    @get:StringRes
    val errorMessageRes: Int?
        get() = when (this) {
            Available -> null
            NoHardware -> R.string.biometric_error_no_hardware
            HardwareUnavailable -> R.string.biometric_error_hw_unavailable
            NoneEnrolled -> R.string.biometric_error_none_enrolled
            SecurityUpdateRequired -> R.string.biometric_error_security_update
            Unsupported -> R.string.biometric_error_unsupported
            Unknown -> R.string.biometric_error_unknown
        }
}
