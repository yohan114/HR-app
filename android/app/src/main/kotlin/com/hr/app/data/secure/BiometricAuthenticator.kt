package com.hr.app.data.secure

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.crypto.Cipher
import kotlin.coroutines.resume

/**
 * Runs a biometric prompt and hands back the cipher it unlocked.
 *
 * ## The cipher is the point
 *
 * A prompt that returns only "the user authenticated" is a UI gesture: the app decides what that
 * permits, and any code path that forgets to ask is a way around it. Passing a `Cipher` through
 * `CryptoObject` makes the authentication *cryptographically* load-bearing — the key is configured
 * with `setUserAuthenticationRequired`, so the Secure Enclave will not perform the operation without
 * a successful match. There is no code path that reads the token without one, because the hardware
 * refuses.
 *
 * That is the difference between this and the product being replaced, whose most-cited complaint is
 * that fingerprint login still asks for a password afterwards.
 */
class BiometricAuthenticator(
    private val activity: FragmentActivity,
) {
    /**
     * Whether this device can do it at all.
     *
     * Checked before offering enrolment, because an offer that fails on tap is worse than no offer.
     * `BIOMETRIC_STRONG` only: weak biometrics cannot back a keystore key that requires user
     * authentication, so a device with only face-unlock-class hardware would enrol and then fail at
     * the moment it mattered.
     */
    fun availability(): BiometricAvailability =
        when (BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            -> BiometricAvailability.NO_HARDWARE
            else -> BiometricAvailability.UNAVAILABLE
        }

    /**
     * Shows the prompt.
     *
     * @param subtitle must say what is about to happen. A vague reason is a dark pattern, and both
     *   platforms' reviewers reject it.
     */
    suspend fun authenticate(
        cipher: Cipher,
        title: String,
        subtitle: String,
    ): BiometricResult =
        suspendCancellableCoroutine { continuation ->
            val prompt =
                BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            val unlocked = result.cryptoObject?.cipher
                            if (continuation.isActive) {
                                continuation.resume(
                                    if (unlocked != null) {
                                        BiometricResult.Success(unlocked)
                                    } else {
                                        // Should not happen with a CryptoObject prompt, and if it
                                        // does the operation must not proceed — an unlocked-looking
                                        // result with no cipher is exactly the case where treating
                                        // the prompt as a gesture would let it through.
                                        BiometricResult.Failed("The prompt returned no cipher")
                                    },
                                )
                            }
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            if (!continuation.isActive) return
                            continuation.resume(
                                when (errorCode) {
                                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                                    BiometricPrompt.ERROR_USER_CANCELED,
                                    BiometricPrompt.ERROR_CANCELED,
                                    -> BiometricResult.Cancelled

                                    // Too many failed attempts. Distinct from a plain failure
                                    // because the UI must stop offering the prompt and fall back
                                    // to a password rather than inviting another attempt.
                                    BiometricPrompt.ERROR_LOCKOUT,
                                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
                                    -> BiometricResult.LockedOut

                                    else -> BiometricResult.Failed(errString.toString())
                                },
                            )
                        }

                        // Deliberately not resumed: a single unrecognised finger is one attempt of
                        // several the system allows. Resolving here would dismiss the prompt on the
                        // first smudge.
                        override fun onAuthenticationFailed() = Unit
                    },
                )

            val info =
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    // "Use password" rather than the device credential. Falling back to the phone's
                    // PIN would weaken the guarantee — a shoulder-surfed screen-lock PIN should not
                    // unlock somebody's payroll.
                    .setNegativeButtonText("Use password")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .build()

            prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))

            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        }
}

enum class BiometricAvailability {
    AVAILABLE,

    /** Hardware exists, nothing enrolled. Worth pointing the user at system settings. */
    NONE_ENROLLED,

    NO_HARDWARE,
    UNAVAILABLE,
    ;

    val canOffer: Boolean get() = this == AVAILABLE
}

sealed interface BiometricResult {
    data class Success(val cipher: Cipher) : BiometricResult

    /** The user dismissed it. Not an error — do not show one. */
    data object Cancelled : BiometricResult

    /** Too many attempts. Stop offering the prompt and require a password. */
    data object LockedOut : BiometricResult

    data class Failed(val reason: String) : BiometricResult
}
