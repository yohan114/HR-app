package com.hr.app.data.auth

import android.os.Build
import com.hr.app.BuildConfig
import com.hr.client.api.AuthenticationApi
import com.hr.client.model.DeviceInfo
import com.hr.client.model.MfaVerifyRequest
import com.hr.client.model.PasswordGrantRequest
import com.hr.client.model.RefreshTokenRequest
import com.hr.client.model.ResolveTenantRequest
import com.hr.client.model.ResolveTenantResponse
import com.hr.client.model.TokenResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sign-in, refresh and sign-out.
 *
 * Every call here goes through the **unauthenticated** HTTP client, and that is a correctness
 * requirement rather than tidiness:
 *
 * - **Recursion.** A refresh made through the authenticated client would hit [AuthInterceptor],
 *   which asks [SessionStore] for a token, which — finding it expired — starts a refresh, which
 *   makes the same call again. The stack would unwind only when the socket timed out.
 * - **The server rejects it anyway.** These endpoints take `X-Tenant-Code`, and presenting a bearer
 *   token alongside it is answered with `TENANT_MISMATCH`. The tenant is authoritative from the
 *   token once one exists, so sending both is a contradiction the server refuses to guess at.
 */
@Singleton
class AuthRepository
    @Inject
    constructor(
        @UnauthenticatedApi private val api: AuthenticationApi,
        private val session: SessionStore,
        private val json: Json,
        private val deviceIdProvider: DeviceIdProvider,
    ) {
        init {
            // Closes the loop: SessionStore refreshes through this repository, and this repository
            // uses the client that SessionStore does *not* authenticate.
            session.attach { refreshToken -> exchangeRefreshToken(refreshToken) }
        }

        /**
         * Resolves an organisation from a work email or an org code.
         *
         * Deliberately does not reveal whether an account exists — it answers about the
         * organisation only, so it cannot be used to enumerate users.
         */
        suspend fun resolveTenant(input: String): Result<ResolveTenantResponse> =
            runCatching {
                val code = input.substringAfterLast('@', input).trim().lowercase()
                api.resolveTenant(code, ResolveTenantRequest(orgCode = code)).orThrow()
            }

        /**
         * Password sign-in.
         *
         * @return [SignInOutcome.MfaRequired] rather than an error when a second factor is
         *   enrolled. It is not a failure — the password was right — and modelling it as one would
         *   push every caller into inspecting an exception to find the happy path.
         */
        suspend fun signIn(
            tenantCode: String,
            username: String,
            password: String,
        ): Result<SignInOutcome> =
            runCatching {
                val response =
                    api.issueToken(
                        tenantCode,
                        PasswordGrantRequest(
                            username = username.trim(),
                            password = password,
                            device = currentDevice(),
                        ),
                    )

                if (response.code() == 401) {
                    val body = response.errorBody()?.string().orEmpty()
                    if (errorCode(body) == "MFA_REQUIRED") {
                        val token =
                            errorDetail(body, "mfaToken")
                                ?: throw AuthException("MFA_REQUIRED", "The server asked for a code but sent no challenge")
                        return@runCatching SignInOutcome.MfaRequired(token)
                    }
                }

                SignInOutcome.SignedIn(adopt(response.orThrow()))
            }

        /** Completes a sign-in that required a second factor. */
        suspend fun verifyMfa(
            tenantCode: String,
            mfaToken: String,
            code: String,
        ): Result<TokenResponse> =
            runCatching {
                adopt(
                    api.verifyMfa(
                        tenantCode,
                        MfaVerifyRequest(mfaToken = mfaToken, code = code.trim(), device = currentDevice()),
                    ).orThrow(),
                )
            }

        suspend fun signOut() {
            session.clear()
        }

        // --------------------------------------------------------------------

        private suspend fun adopt(tokens: TokenResponse): TokenResponse {
            session.adopt(
                SessionStore.Tokens(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                    // The server sends a duration, not an instant, precisely so a device with a
                    // wrong clock still expires the token at the right *interval*. Converting here
                    // means only this one line depends on the local clock being sane.
                    expiresAtMillis = System.currentTimeMillis() + tokens.expiresIn * 1_000L,
                ),
            )
            return tokens
        }

        /**
         * The refresh exchange, called by [SessionStore] under its single-flight gate.
         *
         * Translates `TOKEN_REUSE_DETECTED` into the typed exception the store watches for, so the
         * session is dropped rather than retried — the family is already revoked server-side and
         * every later request would fail in a way the UI could not explain.
         */
        private suspend fun exchangeRefreshToken(refreshToken: String): SessionStore.Tokens {
            val tenantCode = deviceIdProvider.lastTenantCode()
                ?: throw AuthException("NO_TENANT", "No organisation is remembered for this device")

            val response = api.refreshToken(tenantCode, RefreshTokenRequest(refreshToken))

            if (!response.isSuccessful) {
                val body = response.errorBody()?.string().orEmpty()
                if (errorCode(body) == "TOKEN_REUSE_DETECTED") throw TokenReuseDetectedException()
                throw AuthException(errorCode(body) ?: "REFRESH_FAILED", "Could not refresh the session")
            }

            val tokens = response.body() ?: throw AuthException("REFRESH_FAILED", "Empty refresh response")
            return SessionStore.Tokens(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                expiresAtMillis = System.currentTimeMillis() + tokens.expiresIn * 1_000L,
            )
        }

        private fun currentDevice() =
            DeviceInfo(
                deviceId = deviceIdProvider.deviceId(),
                platform = DeviceInfo.Platform.ANDROID,
                model = "${Build.MANUFACTURER} ${Build.MODEL}",
                osVersion = Build.VERSION.RELEASE,
                appVersion = BuildConfig.VERSION_NAME,
            )

        private fun <T> Response<T>.orThrow(): T {
            if (isSuccessful) return body() ?: throw AuthException("EMPTY_RESPONSE", "The server sent no body")
            val body = errorBody()?.string().orEmpty()
            throw AuthException(errorCode(body) ?: "HTTP_${code()}", errorMessage(body) ?: "Request failed")
        }

        /** Reads `error.code` from the standard envelope (docs/03-architecture.md §9). */
        private fun errorCode(body: String): String? = envelopeField(body, "code")

        private fun errorMessage(body: String): String? = envelopeField(body, "message")

        private fun envelopeField(
            body: String,
            field: String,
        ): String? =
            runCatching {
                json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get(field)?.jsonPrimitive?.content
            }.getOrNull()

        private fun errorDetail(
            body: String,
            field: String,
        ): String? =
            runCatching {
                json.parseToJsonElement(body)
                    .jsonObject["error"]?.jsonObject
                    ?.get("details")?.jsonObject
                    ?.get(field)?.jsonPrimitive?.content
            }.getOrNull()
    }

sealed interface SignInOutcome {
    data class SignedIn(val tokens: TokenResponse) : SignInOutcome

    /** The password was correct and a second factor is required. Not an error. */
    data class MfaRequired(val mfaToken: String) : SignInOutcome
}

class AuthException(val code: String, message: String) : RuntimeException(message)
