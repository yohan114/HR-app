package com.hr.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hr.app.data.auth.AuthException
import com.hr.app.data.auth.AuthRepository
import com.hr.app.data.auth.DeviceIdProvider
import com.hr.app.data.auth.SignInOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the sign-in flow.
 *
 * Three steps, in order: which organisation, who are you, and — only when the account has one — a
 * second factor. The organisation step exists so nobody is ever asked to type a "service URL",
 * which is a common source of support load in the product this replaces.
 */
@HiltViewModel
class SignInViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val devices: DeviceIdProvider,
    ) : ViewModel() {
        private val _state = MutableStateFlow(SignInState())
        val state: StateFlow<SignInState> = _state.asStateFlow()

        init {
            // Coming back to a device that has signed in before should not ask which company you
            // work for again.
            devices.lastTenantCode()?.let { remembered ->
                _state.update { it.copy(orgInput = remembered, step = SignInStep.CREDENTIALS, tenantCode = remembered) }
            }
        }

        fun onOrgInputChanged(value: String) = _state.update { it.copy(orgInput = value, error = null) }

        fun onUsernameChanged(value: String) = _state.update { it.copy(username = value, error = null) }

        fun onPasswordChanged(value: String) = _state.update { it.copy(password = value, error = null) }

        fun onCodeChanged(value: String) =
            _state.update {
                // Digits only, capped at six: an authenticator code is never anything else, and
                // silently dropping the space some apps display is kinder than rejecting it.
                it.copy(code = value.filter(Char::isDigit).take(6), error = null)
            }

        /** Step back to the organisation, e.g. after signing in to the wrong one. */
        fun changeOrganisation() =
            _state.update {
                SignInState(orgInput = it.orgInput)
            }

        fun resolveOrganisation() {
            val input = _state.value.orgInput.trim()
            if (input.isEmpty()) return

            submit {
                auth.resolveTenant(input)
                    .onSuccess { tenant ->
                        devices.rememberTenantCode(tenant.code)
                        _state.update {
                            it.copy(
                                step = SignInStep.CREDENTIALS,
                                tenantCode = tenant.code,
                                organisationName = tenant.name,
                                busy = false,
                            )
                        }
                    }
                    .onFailure { failWith(it, fallback = "We could not find that organisation.") }
            }
        }

        fun signIn() {
            val current = _state.value
            if (current.username.isBlank() || current.password.isEmpty()) return

            submit {
                auth.signIn(current.tenantCode, current.username, current.password)
                    .onSuccess { outcome ->
                        when (outcome) {
                            is SignInOutcome.SignedIn ->
                                _state.update { it.copy(busy = false, signedIn = true, password = "") }

                            is SignInOutcome.MfaRequired ->
                                _state.update {
                                    it.copy(
                                        step = SignInStep.MFA,
                                        mfaToken = outcome.mfaToken,
                                        busy = false,
                                        // Not kept beyond this point. The second factor stands on
                                        // its own, and holding the password in memory through
                                        // another screen is a needless window.
                                        password = "",
                                    )
                                }
                        }
                    }
                    .onFailure { failWith(it, fallback = "That username or password is not correct.") }
            }
        }

        fun verifyMfa() {
            val current = _state.value
            if (current.code.length != 6 && current.code.length < RECOVERY_CODE_MIN) return

            submit {
                auth.verifyMfa(current.tenantCode, current.mfaToken, current.code)
                    .onSuccess { _state.update { it.copy(busy = false, signedIn = true, code = "") } }
                    .onFailure { failWith(it, fallback = "That code is not correct.") }
            }
        }

        // --------------------------------------------------------------------

        private fun submit(block: suspend () -> Unit) {
            _state.update { it.copy(busy = true, error = null) }
            viewModelScope.launch { block() }
        }

        /**
         * Turns a failure into something a person can act on.
         *
         * Localised from the machine-readable `code`, never from the server's `message` — those are
         * developer-facing English and sometimes carry internal detail, and this app ships in six
         * languages.
         */
        private fun failWith(
            cause: Throwable,
            fallback: String,
        ) {
            val message =
                when ((cause as? AuthException)?.code) {
                    "INVALID_CREDENTIALS" -> "That username or password is not correct."
                    "ACCOUNT_LOCKED" -> "This account is locked after repeated failed attempts. Try again shortly."
                    "ACCOUNT_DISABLED" -> "This account is not active. Contact your administrator."
                    "TENANT_NOT_FOUND" -> "We could not find that organisation."
                    "MFA_INVALID_CODE" -> "That code is not correct."
                    "RATE_LIMITED" -> "Too many attempts. Please wait a moment and try again."
                    "DEVICE_REVOKED" -> "This device's access has been revoked. Contact your administrator."
                    // A network failure is not a credentials failure, and telling someone their
                    // password is wrong when the train went into a tunnel is worse than useless.
                    null -> if (cause is java.io.IOException) "No connection. Check your network and try again." else fallback
                    else -> fallback
                }
            _state.update { it.copy(busy = false, error = message) }
        }

        private companion object {
            /** Recovery codes are ten characters; the field accepts either. */
            const val RECOVERY_CODE_MIN = 10
        }
    }

enum class SignInStep { ORGANISATION, CREDENTIALS, MFA }

data class SignInState(
    val step: SignInStep = SignInStep.ORGANISATION,
    val orgInput: String = "",
    val tenantCode: String = "",
    val organisationName: String = "",
    val username: String = "",
    val password: String = "",
    val code: String = "",
    val mfaToken: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false,
)
