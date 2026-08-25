package com.hr.identity.internal

import com.hr.shared.api.ErrorCode
import com.hr.shared.api.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Device registration and revocation.
 *
 * Revoking a device is the mechanism that actually withdraws biometric sign-in: the sealed refresh
 * token in the device's secure hardware becomes useless the moment its family is revoked here.
 */
@Service
class DeviceService(
    private val devices: UserDeviceRepository,
    private val refreshTokens: RefreshTokenRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun listForUser(
        userId: UUID,
        currentDeviceId: UUID?,
    ): List<DeviceResponse> =
        devices.findActiveByUserId(userId).map { it.toResponse(current = it.id == currentDeviceId) }

    /**
     * Registers or updates a device.
     *
     * Idempotent on `(user, deviceId)`: re-registering updates in place. Clients call this on every
     * sign-in and whenever the push token rotates, so creating duplicates would accumulate dead
     * device records and fan out push notifications to tokens that no longer work.
     */
    @Transactional
    fun register(
        userId: UUID,
        request: RegisterDeviceRequest,
    ): DeviceResponse {
        val info = request.device
        val existing = devices.findByUserIdAndDeviceId(userId, info.deviceId)

        val device =
            if (existing != null) {
                existing.touch(info.appVersion, info.osVersion, info.pushToken)
                existing.model = info.model ?: existing.model
                // Enrolment is one-way through this endpoint: a client may declare that it has
                // sealed a token, but only an explicit revocation clears the flag. Letting a
                // request set it to false would give an attacker a way to downgrade the account
                // to password-only.
                if (request.biometricEnrolled) existing.biometricEnrolled = true
                existing
            } else {
                UserDevice(
                    userId = userId,
                    deviceId = info.deviceId,
                    platform = info.platformEnum(),
                    model = info.model,
                    osVersion = info.osVersion,
                    appVersion = info.appVersion,
                ).apply {
                    pushToken = info.pushToken?.ifBlank { null }
                    biometricEnrolled = request.biometricEnrolled
                    lastSeenAt = Instant.now()
                }
            }

        // Attestation verification (Play Integrity / App Attest) lands with the mobile clients.
        // Recorded as unverified until then rather than silently claiming otherwise.
        if (request.attestation != null) {
            log.debug("Device attestation supplied for {} but verification is not yet implemented", info.deviceId)
        }

        return devices.save(device).toResponse(current = true)
    }

    /**
     * Revokes a device and every refresh token bound to it.
     *
     * Takes effect immediately for refresh and biometric grants. An access token already issued
     * remains valid until it expires — at most fifteen minutes. Closing that window entirely would
     * require checking a revocation list on every request, which is a real cost for a small
     * benefit; fifteen minutes is the deliberate trade-off.
     */
    @Transactional
    fun revoke(
        userId: UUID,
        deviceId: UUID,
    ) {
        val device =
            devices.findById(deviceId).orElse(null)
                ?: throw NotFoundException(ErrorCode.NOT_FOUND, "Device not found")

        // Scoped to the caller's own devices. RLS keeps this within the tenant; this check keeps
        // one user from revoking another's device inside that tenant.
        if (device.userId != userId) {
            throw NotFoundException(ErrorCode.NOT_FOUND, "Device not found")
        }

        device.revoke(RevocationReason.ADMIN_REVOKED)
        devices.save(device)
        val revoked = refreshTokens.revokeAllForDevice(device.id, RevocationReason.DEVICE_REVOKED, Instant.now())
        log.info("Revoked device {} for user {} and {} refresh tokens", device.deviceId, userId, revoked)
    }

    private fun UserDevice.toResponse(current: Boolean) =
        DeviceResponse(
            id = id,
            deviceId = deviceId,
            platform = platform,
            model = model,
            osVersion = osVersion,
            appVersion = appVersion,
            biometricEnrolled = biometricEnrolled,
            attestationVerified = attestationVerified,
            trusted = trusted,
            lastSeenAt = lastSeenAt,
            current = current,
        )
}
