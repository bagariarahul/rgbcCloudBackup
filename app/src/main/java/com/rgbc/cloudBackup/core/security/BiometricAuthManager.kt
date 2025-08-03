package com.rgbc.cloudBackup.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricAuthManager @Inject constructor(
    private val context: Context
) {

    /**
     * Checks if biometric authentication is available on the device
     */
    fun isBiometricAvailable(): BiometricAuthStatus {
        return when (BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAuthStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAuthStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAuthStatus.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAuthStatus.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricAuthStatus.SECURITY_UPDATE_REQUIRED
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> BiometricAuthStatus.UNSUPPORTED
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> BiometricAuthStatus.UNKNOWN
            else -> BiometricAuthStatus.UNKNOWN
        }
    }

    /**
     * Creates a biometric prompt for authentication
     */
    fun createBiometricPrompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onUserCancel: () -> Unit
    ): BiometricPrompt {
        val executor = ContextCompat.getMainExecutor(context)

        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                            Timber.d("Biometric authentication cancelled by user")
                            onUserCancel()
                        }
                        else -> {
                            Timber.e("Biometric authentication error: $errString")
                            onError(errString.toString())
                        }
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Timber.d("Biometric authentication succeeded")
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Timber.w("Biometric authentication failed - invalid biometric")
                    onError("Authentication failed. Please try again.")
                }
            })

        return biometricPrompt
    }

    /**
     * Creates biometric prompt info with customizable title and subtitle
     */
    fun createPromptInfo(
        title: String = "Authenticate",
        subtitle: String = "Use your biometric credential to access CloudBackup",
        negativeButtonText: String = "Cancel"
    ): BiometricPrompt.PromptInfo {
        return BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
    }
}

enum class BiometricAuthStatus {
    AVAILABLE,
    NO_HARDWARE,
    HARDWARE_UNAVAILABLE,
    NONE_ENROLLED,
    SECURITY_UPDATE_REQUIRED,
    UNSUPPORTED,
    UNKNOWN
}
