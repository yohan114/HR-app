package com.hr.identity.internal.mfa

import com.hr.identity.Caller
import com.hr.identity.internal.AuthenticationService
import com.hr.identity.internal.DeviceInfoDto
import com.hr.identity.internal.TokenResponse
import com.hr.identity.internal.TokenService
import com.hr.shared.api.ErrorCode
import com.hr.shared.api.UnauthenticatedException
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Two-factor enrolment and verification.
 *
 * Split across two security postures, which is why the paths differ:
 *
 * - `/verify` is **unauthenticated** in the session sense. Its caller holds an MFA challenge token
 *   and nothing else, because the password step deliberately did not issue a session.
 * - Everything else requires a normal session, because you must already be signed in to turn a
 *   second factor on, off, or re-issue its recovery codes.
 */
@RestController
@RequestMapping("/v1/auth/mfa")
class MfaController(
    private val mfaService: MfaService,
    private val authenticationService: AuthenticationService,
    private val jwtDecoder: JwtDecoder,
) {
    // ------------------------------------------------------------------------
    // Sign-in
    // ------------------------------------------------------------------------

    /**
     * Exchanges an MFA challenge plus a code for a session.
     *
     * The challenge token is read from the body rather than the `Authorization` header on purpose:
     * it is not a bearer token for this API, and putting it in that header invites middleware,
     * proxies and client libraries to treat it as one.
     */
    @PostMapping("/verify")
    fun verify(
        @Valid @RequestBody request: MfaVerifyRequest,
        @RequestHeader(name = "User-Agent", required = false) userAgent: String?,
    ): TokenResponse {
        val userId = userIdFromChallenge(request.mfaToken)

        mfaService.verify(userId, request.code)

        return authenticationService.completeMfaSignIn(userId, request.device, userAgent)
    }

    // ------------------------------------------------------------------------
    // Enrolment and management — require a live session
    // ------------------------------------------------------------------------

    @GetMapping
    fun status(
        @AuthenticationPrincipal jwt: Jwt,
    ): MfaStatus = mfaService.status(Caller.from(jwt).userId)

    @PostMapping("/enrol")
    fun beginEnrolment(
        @AuthenticationPrincipal jwt: Jwt,
    ): MfaEnrolment = mfaService.beginEnrolment(Caller.from(jwt).userId)

    /** Returns the recovery codes. This is the only time they exist in plaintext. */
    @PostMapping("/enrol/confirm")
    fun confirmEnrolment(
        @Valid @RequestBody request: MfaCodeRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): RecoveryCodesResponse =
        RecoveryCodesResponse(mfaService.confirmEnrolment(Caller.from(jwt).userId, request.code))

    @PostMapping("/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun disable(
        @Valid @RequestBody request: MfaCodeRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ) = mfaService.disable(Caller.from(jwt).userId, request.code)

    @PostMapping("/recovery-codes")
    fun regenerateRecoveryCodes(
        @Valid @RequestBody request: MfaCodeRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): RecoveryCodesResponse =
        RecoveryCodesResponse(mfaService.regenerateRecoveryCodes(Caller.from(jwt).userId, request.code))

    // ------------------------------------------------------------------------

    /**
     * Validates the challenge and extracts the user it was issued for.
     *
     * The `purpose` claim is checked explicitly. Without it an ordinary access token would satisfy
     * this endpoint, which would let anyone with a valid session mint a *second* session for
     * themselves without a second factor — turning MFA off for everyone already signed in.
     */
    private fun userIdFromChallenge(token: String): UUID {
        val jwt =
            runCatching { jwtDecoder.decode(token) }
                .getOrElse {
                    throw UnauthenticatedException(ErrorCode.TOKEN_INVALID, "That challenge is not valid")
                }

        if (jwt.getClaimAsString(TokenService.CLAIM_PURPOSE) != TokenService.PURPOSE_MFA) {
            throw UnauthenticatedException(ErrorCode.TOKEN_INVALID, "That challenge is not valid")
        }

        return runCatching { UUID.fromString(jwt.subject) }
            .getOrElse {
                throw UnauthenticatedException(ErrorCode.TOKEN_INVALID, "That challenge is not valid")
            }
    }
}

data class MfaVerifyRequest(
    @field:NotBlank
    val mfaToken: String,
    @field:NotBlank
    val code: String,
    @field:Valid
    val device: DeviceInfoDto,
)

data class MfaCodeRequest(
    @field:NotBlank
    val code: String,
)

/**
 * Wrapped rather than a bare array, so the response can gain a field — a count, an expiry, a
 * warning — without becoming a breaking change for three generated clients.
 */
data class RecoveryCodesResponse(
    val recoveryCodes: List<String>,
)
