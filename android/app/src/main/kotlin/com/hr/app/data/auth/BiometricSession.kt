package com.hr.app.data.auth

import android.util.Log
import com.hr.app.data.secure.BiometricAuthenticator
import com.hr.app.data.secure.BiometricResult
import com.hr.app.data.secure.CipherPreparation
import com.hr.app.data.secure.SecureTokenStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enrolling a device for biometric unlock, and using it.
 *
 * Sits between [SecureTokenStore], which owns the cryptography, and [SessionStore], which owns the
 * live session. Neither of those knows about a prompt, and this is the only place that does.
 */
@Singleton
class BiometricSession
    @Inject
    constructor(
        private val secureTokenStore: SecureTokenStore,
        private val session: SessionStore,
    ) {
        val hasEnrolledToken: Boolean get() = secureTokenStore.hasSealedToken

        /**
         * Seals the current refresh token behind the user's biometrics.
         *
         * Offered right after a password sign-in, which is the one moment we hold a live refresh
         * token and the user is already present and proven. It is also called again whenever
         * [SessionStore.resealRequired] goes true, because a rotation leaves the sealed copy spent.
         */
        suspend fun enrol(authenticator: BiometricAuthenticator): EnrolResult {
            val refreshToken =
                session.currentRefreshToken()
                    ?: return EnrolResult.NoSession

            if (!authenticator.availability().canOffer) return EnrolResult.Unavailable

            val cipher =
                runCatching { secureTokenStore.prepareSealCipher() }
                    .getOrElse {
                        Log.w(TAG, "Could not prepare a seal cipher", it)
                        return EnrolResult.Failed
                    }

            return when (val result = authenticator.authenticate(cipher, PROMPT_TITLE, ENROL_SUBTITLE)) {
                is BiometricResult.Success -> {
                    runCatching { secureTokenStore.seal(refreshToken, result.cipher) }
                        .onSuccess { session.markSealed() }
                        .fold(
                            onSuccess = { EnrolResult.Enrolled },
                            onFailure = {
                                Log.w(TAG, "Sealing failed after a successful prompt", it)
                                EnrolResult.Failed
                            },
                        )
                }
                BiometricResult.Cancelled -> EnrolResult.Cancelled
                BiometricResult.LockedOut -> EnrolResult.Unavailable
                is BiometricResult.Failed -> EnrolResult.Failed
            }
        }

        /**
         * Unlocks with biometrics and exchanges the sealed token for a session.
         *
         * The exchange matters: the sealed token is a *refresh* token, so unsealing it does not by
         * itself produce a usable session. And it is single-use, so this both spends it and leaves
         * the sealed copy stale — which is why [SessionStore.resealRequired] is set and the caller
         * is expected to re-enrol while the user is still in front of the phone.
         */
        suspend fun unlock(
            authenticator: BiometricAuthenticator,
            exchange: suspend (String) -> Boolean,
        ): UnlockResult {
            val preparation = secureTokenStore.prepareUnsealCipher()

            val ready =
                when (preparation) {
                    is CipherPreparation.Ready -> preparation
                    CipherPreparation.NoSealedToken -> return UnlockResult.NotEnrolled
                    // Biometrics were re-enrolled since sealing. The store has already cleared the
                    // unreadable blob; the user needs to know why they are seeing a password form
                    // rather than concluding the app forgot them.
                    CipherPreparation.KeyInvalidated -> return UnlockResult.BiometricsChanged
                }

            return when (val result = authenticator.authenticate(ready.cipher, PROMPT_TITLE, UNLOCK_SUBTITLE)) {
                is BiometricResult.Success -> {
                    val refreshToken =
                        runCatching { secureTokenStore.unseal(ready.sealedToken, result.cipher) }
                            .getOrElse {
                                Log.w(TAG, "Unsealing failed after a successful prompt", it)
                                return UnlockResult.Failed
                            }

                    if (exchange(refreshToken)) UnlockResult.Unlocked else UnlockResult.Rejected
                }
                BiometricResult.Cancelled -> UnlockResult.Cancelled
                BiometricResult.LockedOut -> UnlockResult.LockedOut
                is BiometricResult.Failed -> UnlockResult.Failed
            }
        }

        /** Called on sign-out and on device revocation. A revoked device must retain nothing usable. */
        fun forget() = secureTokenStore.clear()

        private companion object {
            const val TAG = "BiometricSession"
            const val PROMPT_TITLE = "Unlock HR"
            const val ENROL_SUBTITLE = "Confirm it is you, so next time you can sign in without your password."
            const val UNLOCK_SUBTITLE = "Confirm it is you to sign in."
        }
    }

sealed interface EnrolResult {
    data object Enrolled : EnrolResult

    /** The user declined. Not an error, and worth not asking again this session. */
    data object Cancelled : EnrolResult

    /** No hardware, nothing enrolled on the device, or locked out. */
    data object Unavailable : EnrolResult

    data object NoSession : EnrolResult

    data object Failed : EnrolResult
}

sealed interface UnlockResult {
    data object Unlocked : UnlockResult

    data object NotEnrolled : UnlockResult

    /** Device biometrics changed; the sealed token is gone. Explain, then require a password. */
    data object BiometricsChanged : UnlockResult

    data object Cancelled : UnlockResult

    data object LockedOut : UnlockResult

    /** The server refused the token — expired, revoked, or reuse detected. */
    data object Rejected : UnlockResult

    data object Failed : UnlockResult
}
