package com.hr.identity.internal

import com.hr.shared.persistence.Uuid7
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A refresh token record.
 *
 * Only the SHA-256 hash is stored. A database dump must not yield usable tokens — the same
 * reasoning that applies to passwords applies here, because a refresh token is a bearer credential
 * with a thirty-day life.
 *
 * ## Rotation and families
 *
 * Every refresh mints a new token and marks the old one used. All tokens descended from one login
 * share a `familyId`.
 *
 * If a token that has *already been used* is presented again, there are only two explanations: a
 * client bug replaying a request, or an attacker using a stolen token. We cannot distinguish them,
 * and the cost of guessing wrong in the attacker's favour is an account compromise — so we assume
 * theft and revoke the entire family. Every device on that login session is signed out.
 *
 * This is the standard mitigation from RFC 9700 §4.14.2. It is deliberately aggressive.
 *
 * Note this entity does not extend `TenantScopedEntity`: it is written on the login path before a
 * user is fully authenticated, and it carries no `updated_by`/`version` columns, so the JPA
 * auditing machinery would be dead weight. `tenantId` is set explicitly.
 */
@Entity
@Table(name = "refresh_token")
class RefreshTokenEntity(
    @Column(name = "tenant_id", nullable = false, updatable = false)
    var tenantId: UUID,
    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: UUID,
    @Column(name = "device_id", updatable = false)
    var deviceId: UUID? = null,
    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    var tokenHash: String,
    /** Shared by every token descended from one login. Revoked as a unit on reuse detection. */
    @Column(name = "family_id", nullable = false, updatable = false)
    var familyId: UUID,
    @Column(name = "parent_id", updatable = false)
    var parentId: UUID? = null,
    @Column(name = "expires_at", nullable = false, updatable = false)
    var expiresAt: Instant,
) {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID = Uuid7.generate()
        protected set

    @Column(name = "issued_at", nullable = false, updatable = false)
    var issuedAt: Instant = Instant.now()
        protected set

    @Column(name = "used_at")
    var usedAt: Instant? = null

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null

    @Column(name = "revoked_reason", length = 64)
    var revokedReason: String? = null

    val isExpired: Boolean
        get() = expiresAt.isBefore(Instant.now())

    val isUsed: Boolean
        get() = usedAt != null

    val isRevoked: Boolean
        get() = revokedAt != null

    val isUsable: Boolean
        get() = !isExpired && !isUsed && !isRevoked

    fun markUsed() {
        usedAt = Instant.now()
    }

    fun revoke(reason: String) {
        if (revokedAt == null) {
            revokedAt = Instant.now()
            revokedReason = reason
        }
    }
}

/** Why a token or family was revoked. Recorded for security review, not shown to users. */
object RevocationReason {
    const val LOGOUT = "LOGOUT"
    const val ROTATED = "ROTATED"
    const val REUSE_DETECTED = "REUSE_DETECTED"
    const val DEVICE_REVOKED = "DEVICE_REVOKED"
    const val PASSWORD_CHANGED = "PASSWORD_CHANGED"
    const val ACCOUNT_DISABLED = "ACCOUNT_DISABLED"
    const val ADMIN_REVOKED = "ADMIN_REVOKED"
}
