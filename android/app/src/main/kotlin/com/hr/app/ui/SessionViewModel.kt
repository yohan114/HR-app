package com.hr.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hr.app.data.auth.AuthRepository
import com.hr.app.data.auth.BiometricSession
import com.hr.app.data.auth.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Whether the app is signed in.
 *
 * Reads the session at construction rather than observing it, because [SessionStore] holds tokens
 * in memory only: a cold start always begins signed out until either a password sign-in or a
 * biometric unlock restores one. Observing a value that can only change through this class's own
 * methods would be indirection without benefit.
 */
@HiltViewModel
class SessionViewModel
    @Inject
    constructor(
        private val session: SessionStore,
        private val auth: AuthRepository,
        private val biometric: BiometricSession,
    ) : ViewModel() {
        private val _signedIn = MutableStateFlow(session.hasSession)
        val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

        /**
         * Whether a cold start should offer biometric unlock rather than the password form.
         *
         * Read once, at construction. A device either has a sealed token when the app launches or
         * it does not, and the only things that change that — enrolling, or the store clearing an
         * invalidated key — happen after this decision has already been made.
         */
        private val _startWithBiometric = MutableStateFlow(!session.hasSession && biometric.hasEnrolledToken)
        val startWithBiometric: StateFlow<Boolean> = _startWithBiometric.asStateFlow()

        fun onSignedIn() {
            _signedIn.value = session.hasSession
            _startWithBiometric.value = false
        }

        /** The user chose the password form over the prompt, or there was nothing to unlock. */
        fun usePasswordInstead() {
            _startWithBiometric.value = false
        }

        /**
         * Signs out.
         *
         * The UI flips first and the clearing happens behind it. Waiting would leave a signed-out
         * user looking at their own payroll data for as long as the coroutine took to schedule, and
         * there is nothing to fail: dropping in-memory tokens cannot throw.
         */
        fun signOut() {
            _signedIn.value = false
            viewModelScope.launch { auth.signOut() }
        }
    }
