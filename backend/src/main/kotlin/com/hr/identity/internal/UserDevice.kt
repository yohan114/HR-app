package com.hr.identity.internal

import com.hr.shared.persistence.TenantScopedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A device registered to a user.
 *
 * Devices matter here for one specific reason: the biometric login flow. The refresh token is
 * sealed into this device's secure hardware at enrolment, so revoking the device is what actually
 * takes away the ability to sign in without a password. There is no separate "biometric
 * credential" to revoke — the device *is* the credential.
 */
@Entity
@Table(name = "user_device")
class UserDevice(
    @Column(name = "user_id", nullable = false)
    var userId: UUID,
    /** Client-generated stable identifier. Unique per (tenant, user). */
    @Column(name = "device_id", nullable = false, length = 128)
    var deviceId: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 16)
    var platform: DevicePlatform,
    @Column(name = "model", length = 128)
    var model: String? = null,
    @Column(name = "os_version", length = 64)
    var osVersion: String? = null,
    @Column(name = "app_version", length = 32)
    var appVersion: String? = null,
) : TenantScopedEntity() {
    @Column(name = "push_token")
    var pushToken: String? = null

    @Column(name = "biometric_enrolled", nullable = false)
    var biometricEnrolled: Boolean = false

    /** Play Integrity / App Attest verified. Informational in Phase 0; enforced later. */
    @Column(name = "attestation_verified", nullable = false)
    var attestationVerified: Boolean = false

    @Column(name = "trusted", nullable = false)
    var trusted: Boolean = true

    @Column(name = "last_seen_at")
    var lastSeenAt: Instant? = null

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null

    @Column(name = "revoked_reason", length = 255)
    var revokedReason: String? = null

    val isActive: Boolean
        get() = revokedAt == null && trusted

    fun revoke(reason: String) {
        revokedAt = Instant.now()
        revokedReason = reason
        biometricEnrolled = false
    }

    fun touch(
        appVersion: String?,
        osVersion: String?,
        pushToken: String?,
    ) {
        lastSeenAt = Instant.now()
        appVersion?.let { this.appVersion = it }
        osVersion?.let { this.osVersion = it }
        // Push tokens rotate; an empty string is the client's way of saying "I no longer have one".
        pushToken?.let { this.pushToken = it.ifBlank { null } }
    }
}

enum class DevicePlatform { ANDROID, IOS, WEB, KIOSK }
