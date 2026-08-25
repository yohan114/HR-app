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
 * A login account.
 *
 * Deliberately separate from `employee` (Phase 1). Not every employee has a login — factory and
 * retail staff often clock in at a shared kiosk and never touch the app — and not every user is an
 * employee: implementation consultants, external auditors and support staff all need accounts.
 * Collapsing the two is a modelling mistake that is expensive to unpick once payroll and
 * attendance reference employees.
 */
@Entity
@Table(name = "app_user")
class AppUser(
    @Column(name = "username", nullable = false)
    var username: String,
    @Column(name = "email")
    var email: String? = null,
    /** Argon2id. Null for SSO-only accounts. */
    @Column(name = "password_hash")
    var passwordHash: String? = null,
    @Column(name = "employee_id")
    var employeeId: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: UserStatus = UserStatus.ACTIVE,
    @Column(name = "locale", length = 16)
    var locale: String? = null,
    @Column(name = "timezone", length = 64)
    var timezone: String? = null,
) : TenantScopedEntity() {
    @Column(name = "password_changed_at")
    var passwordChangedAt: Instant? = null

    @Column(name = "must_change_password", nullable = false)
    var mustChangePassword: Boolean = false

    @Column(name = "mfa_enabled", nullable = false)
    var mfaEnabled: Boolean = false

    @Column(name = "mfa_secret_enc")
    var mfaSecretEnc: String? = null

    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null

    @Column(name = "failed_attempts", nullable = false)
    var failedAttempts: Short = 0

    @Column(name = "locked_until")
    var lockedUntil: Instant? = null

    val isLocked: Boolean
        get() = lockedUntil?.isAfter(Instant.now()) == true

    /**
     * Whether this account may authenticate at all.
     *
     * Note that a locked account is *not* disabled — lockout is temporary and self-clearing, so it
     * is checked separately and reported with a different error code. Conflating the two produces
     * the classic support ticket where a user is told their account is disabled when they simply
     * need to wait fifteen minutes.
     */
    val canAuthenticate: Boolean
        get() = status == UserStatus.ACTIVE && !isLocked

    fun recordSuccessfulLogin() {
        lastLoginAt = Instant.now()
        failedAttempts = 0
        lockedUntil = null
    }

    /**
     * Records a failed attempt and locks the account once the threshold is reached.
     *
     * Returns true if this failure caused a lockout, so the caller can audit it distinctly.
     */
    fun recordFailedLogin(
        threshold: Short,
        lockoutMinutes: Short,
    ): Boolean {
        failedAttempts = (failedAttempts + 1).toShort()
        if (failedAttempts >= threshold) {
            lockedUntil = Instant.now().plusSeconds(lockoutMinutes * 60L)
            return true
        }
        return false
    }
}

enum class UserStatus { ACTIVE, DISABLED, LOCKED, PENDING_ACTIVATION }
