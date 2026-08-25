package com.hr.identity.internal

import com.hr.shared.persistence.Uuid7
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * An authentication attempt, successful or not.
 *
 * Kept separate from the general audit log because it is read on a different access path — "where
 * am I signed in?", account lockout investigation, security review after a suspected compromise —
 * and retained on a different schedule.
 *
 * Failures are recorded even when the username does not exist, with a null `userId`. That is the
 * data you need to spot credential-stuffing, and it is unavailable if you only log successes.
 */
@Entity
@Table(name = "login_event")
class LoginEvent(
    @Column(name = "tenant_id", nullable = false, updatable = false)
    var tenantId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 32)
    var method: LoginMethod,
    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 32)
    var result: LoginResult,
    @Column(name = "user_id")
    var userId: UUID? = null,
    @Column(name = "device_id")
    var deviceId: UUID? = null,
    /** Recorded even on failure, so a stuffing attempt against unknown accounts is visible. */
    @Column(name = "username", length = 255)
    var username: String? = null,
    @Column(name = "failure_code", length = 64)
    var failureCode: String? = null,
    @Column(name = "user_agent")
    var userAgent: String? = null,
) {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID = Uuid7.generate()
        protected set

    @Column(name = "occurred_at", nullable = false, updatable = false)
    var occurredAt: Instant = Instant.now()
        protected set
}

enum class LoginMethod { PASSWORD, BIOMETRIC, REFRESH, SSO, MFA }

enum class LoginResult { SUCCESS, FAILURE, LOCKED, MFA_REQUIRED }
