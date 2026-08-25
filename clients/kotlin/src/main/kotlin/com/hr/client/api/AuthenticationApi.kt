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
import com.hr.client.model.PasswordGrantRequest
import com.hr.client.model.RefreshTokenRequest
import com.hr.client.model.RegisterDeviceRequest
import com.hr.client.model.ResolveTenantRequest
import com.hr.client.model.ResolveTenantResponse
import com.hr.client.model.TokenResponse

interface AuthenticationApi {
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

}
