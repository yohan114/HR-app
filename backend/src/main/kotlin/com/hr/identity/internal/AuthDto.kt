package com.hr.identity.internal

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

// ---------------------------------------------------------------------------
// Requests
// ---------------------------------------------------------------------------

data class PasswordGrantRequest(
    @field:NotBlank
    val username: String,
    @field:NotBlank
    val password: String,
    @field:Valid
    val device: DeviceInfoDto,
)

data class RefreshTokenRequest(
    @field:NotBlank
    val refreshToken: String,
)

data class BiometricGrantRequest(
    /**
     * The refresh token released from the device's secure hardware after a successful biometric
     * assertion.
     *
     * The server never sees biometric data. It trusts the *presentation* of this token, because
     * the OS will only release it after a fingerprint or face match, and invalidates the sealing
     * key outright if the device's enrolled biometrics change.
     */
    @field:NotBlank
    val sealedRefreshToken: String,
    @field:NotBlank
    val deviceId: String,
)

data class DeviceInfoDto(
    @field:NotBlank
    @field:Size(max = 128)
    val deviceId: String,
    @field:NotBlank
    val platform: String,
    @field:Size(max = 128)
    val model: String? = null,
    @field:Size(max = 64)
    val osVersion: String? = null,
    @field:Size(max = 32)
    val appVersion: String? = null,
    val pushToken: String? = null,
) {
    fun platformEnum(): DevicePlatform =
        runCatching { DevicePlatform.valueOf(platform.uppercase()) }
            .getOrElse { DevicePlatform.WEB }
}

data class RegisterDeviceRequest(
    @field:Valid
    val device: DeviceInfoDto,
    val attestation: String? = null,
    /**
     * Whether the client has sealed its refresh token into secure hardware.
     *
     * Set by the client after a successful `BiometricPrompt` / `LAContext` enrolment. The server
     * records it so that the biometric grant can reject devices that never actually enrolled.
     */
    val biometricEnrolled: Boolean = false,
)

data class ResolveTenantRequest(
    val email: String? = null,
    val orgCode: String? = null,
)

// ---------------------------------------------------------------------------
// Responses
// ---------------------------------------------------------------------------

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val refreshExpiresIn: Long,
    /**
     * True when this device has not yet sealed a refresh token.
     *
     * The client should offer biometric enrolment immediately after first sign-in. Deferring it to
     * a settings screen is precisely how users end up typing their password forever — which is the
     * single most-complained-about behaviour in the product we are replacing.
     */
    val biometricEnrolmentOffered: Boolean = false,
    val mustChangePassword: Boolean = false,
)

data class DeviceResponse(
    val id: UUID,
    val deviceId: String,
    val platform: DevicePlatform,
    val model: String?,
    val osVersion: String?,
    val appVersion: String?,
    val biometricEnrolled: Boolean,
    val attestationVerified: Boolean,
    val trusted: Boolean,
    val lastSeenAt: Instant?,
    /** True for the device making this request, so the UI can warn before self-revocation. */
    val current: Boolean,
)

data class ResolveTenantResponse(
    val code: String,
    val name: String,
    val locale: String,
    val defaultCurrency: String,
    val timezone: String,
    val authMethods: List<String>,
)

data class MeResponse(
    val userId: UUID,
    val employeeId: UUID?,
    val username: String,
    val email: String?,
    val locale: String?,
    val timezone: String?,
    val tenant: TenantSummaryDto,
    val roles: List<String>,
    /**
     * Effective permission keys.
     *
     * The client uses these to hide actions the user cannot perform. It is a UX affordance, not a
     * security control — the API enforces every permission server-side regardless of what the
     * client believes.
     */
    val permissions: List<String>,
    val enabledModules: List<String>,
    val mustChangePassword: Boolean,
    val mfaEnabled: Boolean,
)

data class TenantSummaryDto(
    val id: UUID,
    val code: String,
    val name: String,
    val defaultCurrency: String,
    val timezone: String,
    val locale: String,
)
