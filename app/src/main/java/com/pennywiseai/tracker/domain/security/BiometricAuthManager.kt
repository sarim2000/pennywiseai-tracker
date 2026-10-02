package com.pennywiseai.tracker.domain.security

import android.content.Context
import androidx.annotation.StringRes
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
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
        return when (biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)) {
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
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = activity.getString(R.string.applock_prompt_title),
        subtitle: String = activity.getString(R.string.applock_prompt_subtitle),
        description: String = activity.getString(R.string.applock_prompt_description),
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(context)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription(description)
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Don't treat user cancellation as an error
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        onError(errString.toString())
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
