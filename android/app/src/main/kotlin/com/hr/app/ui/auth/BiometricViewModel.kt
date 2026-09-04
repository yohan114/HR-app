package com.hr.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hr.app.data.auth.AuthRepository
import com.hr.app.data.auth.BiometricSession
import com.hr.app.data.auth.EnrolResult
import com.hr.app.data.auth.SessionStore
import com.hr.app.data.auth.UnlockResult
import com.hr.app.data.secure.BiometricAuthenticator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Biometric unlock and enrolment.
 *
 * Two flows that share a prompt and nothing else:
 *
 * - **Unlock**, on a cold start where a sealed token exists. This is the feature — no password,
 *   ever again, until the token expires or the device is revoked.
 * - **Enrol**, offered right after a password sign-in, and again whenever a rotation has left the
 *   sealed copy spent.
 */
@HiltViewModel
class BiometricViewModel
    @Inject
    constructor(
        private val biometric: BiometricSession,
        private val auth: AuthRepository,
        private val session: SessionStore,
    ) : ViewModel() {
        private val _state = MutableStateFlow(BiometricState(canUnlock = biometric.hasEnrolledToken))
        val state: StateFlow<BiometricState> = _state.asStateFlow()

        /**
         * Whether to offer enrolment.
         *
         * Offered when the device can do it and the sealed copy is missing or stale. `resealRequired`
         * is the subtle half: a refresh rotates the token, which leaves whatever is sealed already
         * spent — and presenting a spent token on the next cold start is read by the server as
         * theft, which revokes the family and signs the user out everywhere.
         */
        fun shouldOfferEnrolment(authenticator: BiometricAuthenticator): Boolean =
            authenticator.availability().canOffer &&
                session.hasSession &&
                (!biometric.hasEnrolledToken || session.resealRequired)

        fun unlock(
            authenticator: BiometricAuthenticator,
            onUnlocked: () -> Unit,
        ) {
            _state.update { it.copy(busy = true, message = null) }

            viewModelScope.launch {
                val result = biometric.unlock(authenticator) { auth.signInWithBiometric(it) }

                _state.update {
                    when (result) {
                        UnlockResult.Unlocked -> it.copy(busy = false, message = null)

                        // Dismissing the prompt is a choice, not a failure. Showing an error would
                        // scold someone for deciding to type their password instead.
                        UnlockResult.Cancelled -> it.copy(busy = false, message = null)

                        UnlockResult.BiometricsChanged ->
                            it.copy(
                                busy = false,
                                canUnlock = false,
                                message =
                                    "Your device's fingerprint or face setup changed, so the saved sign-in " +
                                        "was cleared for safety. Please sign in with your password.",
                            )

                        UnlockResult.LockedOut ->
                            it.copy(
                                busy = false,
                                canUnlock = false,
                                message = "Too many attempts. Please sign in with your password.",
                            )

                        UnlockResult.Rejected ->
                            it.copy(
                                busy = false,
                                canUnlock = false,
                                message = "Your saved sign-in has expired. Please sign in with your password.",
                            )

                        UnlockResult.NotEnrolled -> it.copy(busy = false, canUnlock = false, message = null)

                        UnlockResult.Failed ->
                            it.copy(busy = false, message = "Could not unlock. Please sign in with your password.")
                    }
                }

                if (result == UnlockResult.Unlocked) onUnlocked()
            }
        }

        fun enrol(
            authenticator: BiometricAuthenticator,
            onFinished: () -> Unit,
        ) {
            _state.update { it.copy(busy = true, message = null) }

            viewModelScope.launch {
                val result = biometric.enrol(authenticator)

                _state.update {
                    it.copy(
                        busy = false,
                        message =
                            when (result) {
                                EnrolResult.Enrolled, EnrolResult.Cancelled, EnrolResult.NoSession -> null
                                EnrolResult.Unavailable -> null
                                EnrolResult.Failed -> "Could not set that up. You can try again from Settings."
                            },
                    )
                }

                // Enrolment is an offer, not a gate. Declining it, or failing it, must still leave
                // the user inside the app they have already authenticated into.
                onFinished()
            }
        }

        fun dismissMessage() = _state.update { it.copy(message = null) }
    }

data class BiometricState(
    val canUnlock: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)
