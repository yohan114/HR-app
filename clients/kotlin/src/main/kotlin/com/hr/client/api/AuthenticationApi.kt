package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.BiometricGrantRequest
import com.hr.client.model.Device
import com.hr.client.model.JwkSet
import com.hr.client.model.MfaCodeRequest
import com.hr.client.model.MfaEnrolment
import com.hr.client.model.MfaStatus
import com.hr.client.model.MfaVerifyRequest
import com.hr.client.model.PasswordGrantRequest
import com.hr.client.model.RecoveryCodesResponse
import com.hr.client.model.RefreshTokenRequest
import com.hr.client.model.RegisterDeviceRequest
import com.hr.client.model.ResolveTenantRequest
import com.hr.client.model.ResolveTenantResponse
import com.hr.client.model.TokenResponse

interface AuthenticationApi {
    /**
     * POST v1/auth/mfa/enrol
     * Begin enrolment — returns a secret and a QR payload
     * Issues a TOTP secret and the &#x60;otpauth://&#x60; URI an authenticator app reads from a QR code. The secret is stored immediately but **MFA is not switched on** until &#x60;/v1/auth/mfa/enrol/confirm&#x60; succeeds.  Two steps rather than one deliberately: a user who mis-scans, or scans into an app they then delete, would otherwise lock themselves out of their own account with no route back except an administrator — which is a support call and a social-engineering path straight past the factor. Calling this again replaces the pending secret, which is what someone does after scanning into the wrong app. 
     * Responses:
     *  - 200: Enrolment started
     *  - 422: Syntactically valid but rejected by a domain rule. `details.violations` lists **every** problem, not just the first — reporting one at a time turns filling a form into a guessing game. 
     *
     * @return [MfaEnrolment]
     */
    @POST("v1/auth/mfa/enrol")
    suspend fun beginMfaEnrolment(): Response<MfaEnrolment>

    /**
     * POST v1/auth/token/biometric
     * Exchange a device-sealed refresh token after a biometric assertion
     * The core of the \&quot;no password after enrolment\&quot; experience.  The refresh token is sealed at enrolment in the device&#39;s Android Keystore or iOS Secure Enclave under a key that requires user authentication. The OS releases it only after a successful fingerprint or face match, and invalidates the key entirely if the device&#39;s enrolled biometrics change.  The server therefore trusts the *presentation* of the sealed token as evidence that the biometric check passed — it never receives or stores biometric data itself.  Two conditions beyond a normal refresh: the token must have been issued to the device presenting it, and that device must have completed biometric enrolment. Together these stop a token exfiltrated from one device being replayed from another. 
     * Responses:
     *  - 200: Authenticated
     *  - 401: Authentication required or token invalid
     *  - 403: Device revoked, or re-authentication with a password is required
     *
     * @param xTenantCode Organisation code. Required on unauthenticated endpoints, where no token exists yet.
     * @param biometricGrantRequest 
     * @return [TokenResponse]
     */
    @POST("v1/auth/token/biometric")
    suspend fun biometricToken(@Header("X-Tenant-Code") xTenantCode: kotlin.String, @Body biometricGrantRequest: BiometricGrantRequest): Response<TokenResponse>

    /**
     * POST v1/auth/mfa/enrol/confirm
     * Confirm enrolment with a code from the authenticator
     * Proves the app and the server agree before anything depends on it, then switches MFA on.  **Returns the recovery codes, and this is the only time they exist in plaintext.** They are stored as hashes, so neither the server nor a database backup can produce them again. A client that does not show them here has lost them for that user. 
     * Responses:
     *  - 200: Enrolled — show these codes once
     *  - 401: Authentication required or token invalid
     *  - 422: Syntactically valid but rejected by a domain rule. `details.violations` lists **every** problem, not just the first — reporting one at a time turns filling a form into a guessing game. 
     *  - 429: Too many requests
     *
     * @param mfaCodeRequest 
     * @return [RecoveryCodesResponse]
     */
    @POST("v1/auth/mfa/enrol/confirm")
    suspend fun confirmMfaEnrolment(@Body mfaCodeRequest: MfaCodeRequest): Response<RecoveryCodesResponse>

    /**
     * POST v1/auth/mfa/disable
     * Turn the second factor off
     * Requires a current code — possession, not just a live session. Without that, anyone who borrowed an unlocked laptop could switch the factor off from a settings screen, which makes having it decorative. 
     * Responses:
     *  - 204: Disabled
     *  - 401: Authentication required or token invalid
     *  - 429: Too many requests
     *
     * @param mfaCodeRequest 
     * @return [Unit]
     */
    @POST("v1/auth/mfa/disable")
    suspend fun disableMfa(@Body mfaCodeRequest: MfaCodeRequest): Response<Unit>

    /**
     * GET v1/auth/.well-known/jwks.json
     * Public JSON Web Key Set
     * The public half of the key used to sign access tokens, so that other services, an API gateway or a third-party integration can verify our tokens without holding any credential of their own.  The application itself does not call this endpoint — it verifies with the in-process public key rather than issuing an HTTP request to itself.  During a key rotation window this returns both the outgoing and incoming keys, so tokens signed with either continue to verify. 
     * Responses:
     *  - 200: JWK set
     *
     * @return [JwkSet]
     */
    @GET("v1/auth/.well-known/jwks.json")
    suspend fun getJwks(): Response<JwkSet>

    /**
     * GET v1/auth/mfa
     * Whether a second factor is enrolled
     * Drives the security settings screen: whether to offer enrolment, resume a half-finished one, or warn that recovery codes are running low. 
     * Responses:
     *  - 200: Current state
     *
     * @return [MfaStatus]
     */
    @GET("v1/auth/mfa")
    suspend fun getMfaStatus(): Response<MfaStatus>

    /**
     * POST v1/auth/token
     * Sign in with a password
     * Exchanges credentials for an access and refresh token pair, and registers the calling device in the same round trip so the client can offer biometric enrolment immediately.  Failures are deliberately uniform: a wrong password and an unknown username both return &#x60;INVALID_CREDENTIALS&#x60;, so this endpoint cannot be used to enumerate accounts. Repeated failures lock the account per the tenant&#39;s password policy. 
     * Responses:
     *  - 200: Authenticated
     *  - 401: Invalid credentials, or MFA is required. `MFA_REQUIRED` carries an `mfaToken` in `details` which must be presented to `/v1/auth/mfa/verify`. 
     *  - 403: Authenticated but not permitted
     *  - 429: Too many requests
     *
     * @param xTenantCode Organisation code. Required on unauthenticated endpoints, where no token exists yet.
     * @param passwordGrantRequest 
     * @return [TokenResponse]
     */
    @POST("v1/auth/token")
    suspend fun issueToken(@Header("X-Tenant-Code") xTenantCode: kotlin.String, @Body passwordGrantRequest: PasswordGrantRequest): Response<TokenResponse>

    /**
     * GET v1/auth/devices
     * List the current user&#39;s registered devices
     * Powers the \&quot;where am I signed in?\&quot; screen. The device making the request is flagged with &#x60;current: true&#x60; so the client can avoid offering the user a way to revoke themselves without warning. 
     * Responses:
     *  - 200: Devices
     *
     * @return [kotlin.collections.List<Device>]
     */
    @GET("v1/auth/devices")
    suspend fun listDevices(): Response<kotlin.collections.List<Device>>

    /**
     * POST v1/auth/logout
     * Sign out
     * Revokes the token family the presented refresh token belongs to. Other devices stay signed in — signing a user out everywhere is a separate, deliberate action.  The body is optional. Without it the caller&#39;s entire set of sessions is revoked, which is the correct fallback when a client has lost track of its refresh token.  Never fails: sign-out is idempotent and a client must always be able to end its session. 
     * Responses:
     *  - 204: Signed out
     *
     * @param refreshTokenRequest  (optional)
     * @return [Unit]
     */
    @POST("v1/auth/logout")
    suspend fun logout(@Body refreshTokenRequest: RefreshTokenRequest? = null): Response<Unit>

    /**
     * POST v1/auth/token/refresh
     * Exchange a refresh token for a new access token
     * Refresh tokens are single-use and rotate on every exchange.  If a token that has already been used is presented again, the most likely explanation is theft, so the entire token family is revoked and every device sharing that login session must re-authenticate. The response is &#x60;TOKEN_REUSE_DETECTED&#x60;.  Requires &#x60;X-Tenant-Code&#x60;: the token lookup is itself tenant-scoped by row-level security, so the tenant must be known before the token can be found. 
     * Responses:
     *  - 200: Rotated
     *  - 401: Authentication required or token invalid
     *
     * @param xTenantCode Organisation code. Required on unauthenticated endpoints, where no token exists yet.
     * @param refreshTokenRequest 
     * @return [TokenResponse]
     */
    @POST("v1/auth/token/refresh")
    suspend fun refreshToken(@Header("X-Tenant-Code") xTenantCode: kotlin.String, @Body refreshTokenRequest: RefreshTokenRequest): Response<TokenResponse>

    /**
     * POST v1/auth/mfa/recovery-codes
     * Issue a fresh set of recovery codes
     * Invalidates every existing code. Requires a current code, for the same reason as disabling. 
     * Responses:
     *  - 200: New codes — show these once
     *  - 401: Authentication required or token invalid
     *  - 429: Too many requests
     *
     * @param mfaCodeRequest 
     * @return [RecoveryCodesResponse]
     */
    @POST("v1/auth/mfa/recovery-codes")
    suspend fun regenerateRecoveryCodes(@Body mfaCodeRequest: MfaCodeRequest): Response<RecoveryCodesResponse>

    /**
     * POST v1/auth/devices
     * Register the current device
     * Registers or updates a device for the authenticated user, including its push token.  Called on sign-in and again whenever the push token rotates. Idempotent on &#x60;(user, deviceId)&#x60;: re-registering an existing device updates it rather than creating a duplicate. 
     * Responses:
     *  - 201: Registered
     *
     * @param registerDeviceRequest 
     * @return [Device]
     */
    @POST("v1/auth/devices")
    suspend fun registerDevice(@Body registerDeviceRequest: RegisterDeviceRequest): Response<Device>

    /**
     * POST v1/auth/resolve-tenant
     * Resolve an organisation from a work email or org code
     * The first step of sign-in. Deliberately does **not** reveal whether a user account exists — it resolves the organisation only, so this endpoint cannot be used to enumerate accounts.  This replaces asking the user to type a \&quot;service URL\&quot;, which is a common source of support load in comparable products. 
     * Responses:
     *  - 200: Organisation resolved
     *  - 404: Not found
     *  - 429: Too many requests
     *
     * @param xTenantCode Organisation code. Required on unauthenticated endpoints, where no token exists yet.
     * @param resolveTenantRequest 
     * @return [ResolveTenantResponse]
     */
    @POST("v1/auth/resolve-tenant")
    suspend fun resolveTenant(@Header("X-Tenant-Code") xTenantCode: kotlin.String, @Body resolveTenantRequest: ResolveTenantRequest): Response<ResolveTenantResponse>

    /**
     * DELETE v1/auth/devices/{id}
     * Revoke a device
     * Revokes the device and every refresh token bound to it. Takes effect immediately for refresh and biometric grants; an access token already issued remains valid until it expires, at most fifteen minutes.  Note the parameter is the device record&#39;s &#x60;id&#x60; (a UUID), not its &#x60;deviceId&#x60; (the client-generated string). The two are distinct and naming the path variable &#x60;deviceId&#x60; was actively misleading. 
     * Responses:
     *  - 204: Revoked
     *  - 404: Not found
     *
     * @param id The device record&#39;s UUID, as returned by &#x60;GET /v1/auth/devices&#x60;.
     * @return [Unit]
     */
    @DELETE("v1/auth/devices/{id}")
    suspend fun revokeDevice(@Path("id") id: java.util.UUID): Response<Unit>

    /**
     * POST v1/auth/mfa/verify
     * Complete a sign-in with a second factor
     * The second half of a password sign-in. Called after &#x60;/v1/auth/token&#x60; answers &#x60;401 MFA_REQUIRED&#x60; with an &#x60;mfaToken&#x60; in &#x60;details&#x60;.  Accepts either a TOTP code or a recovery code — the user cannot always tell you which they are holding, and making them choose adds a decision at the worst possible moment. A recovery code is consumed on use.  The challenge token goes in the **body**, not the &#x60;Authorization&#x60; header: it is not a bearer token for this API, and putting it there invites proxies and client libraries to treat it as one. It carries no roles and no employee id, so even if something did accept it as a session it would grant nothing.  Every failure — wrong code, spent recovery code, account without a second factor — returns the same &#x60;MFA_INVALID_CODE&#x60;. Distinguishing them would tell whoever holds the challenge which case they are in. 
     * Responses:
     *  - 200: Authenticated
     *  - 401: Authentication required or token invalid
     *  - 429: Too many requests
     *
     * @param xTenantCode Organisation code. Required on unauthenticated endpoints, where no token exists yet.
     * @param mfaVerifyRequest 
     * @return [TokenResponse]
     */
    @POST("v1/auth/mfa/verify")
    suspend fun verifyMfa(@Header("X-Tenant-Code") xTenantCode: kotlin.String, @Body mfaVerifyRequest: MfaVerifyRequest): Response<TokenResponse>

}
