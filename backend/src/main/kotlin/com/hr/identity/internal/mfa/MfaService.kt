package com.hr.identity.internal.mfa

import com.hr.identity.internal.AppUser
import com.hr.identity.internal.LoginMethod
import com.hr.identity.internal.AppUserRepository
import com.hr.shared.api.BusinessRuleException
import com.hr.shared.api.ErrorCode
import com.hr.shared.api.NotFoundException
import com.hr.shared.api.RateLimitedException
import com.hr.shared.api.UnauthenticatedException
import com.hr.shared.crypto.FieldCipher
import com.hr.tenancy.TenantContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Enrolment and verification of the second factor.
 *
 * ## Enrolment is two steps, and that is not ceremony
 *
 * `begin` issues a secret and a QR code; `confirm` requires a working code from the authenticator
 * before MFA is switched on. Enabling it in one step would let a user who mis-scanned — or scanned
 * into an app they then deleted — lock themselves out of their own account with no way back except
 * an administrator. The confirmation proves the app and the server agree *before* anything depends
 * on it.
 *
 * ## Attempts are rate-limited, in memory, per user
 *
 * A six-digit code accepted across a ±1 step window is one in ~333,000 per guess. That is ample
 * against a human and thin against a script, so verification is capped. The counter is in memory
 * rather than the database because it must survive nothing: a restart clearing it costs an
 * attacker a restart's worth of delay, while a database write per failed attempt would make the
 * login path slower for everyone to defend against a case that is already rate-limited upstream.
 */
@Service
class MfaService(
    private val users: AppUserRepository,
    private val totp: TotpGenerator,
    private val recoveryCodes: RecoveryCodes,
    private val cipher: FieldCipher,
    @Value("\${hr.auth.issuer-name:HR}") private val issuerName: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val attempts = ConcurrentHashMap<AttemptKey, Attempts>()

    // ------------------------------------------------------------------------
    // Enrolment
    // ------------------------------------------------------------------------

    /**
     * Starts enrolment: a secret, and the URI an authenticator app scans.
     *
     * The secret is stored immediately but `mfa_enabled` stays false, so an abandoned enrolment
     * leaves the account exactly as it was. Re-running this replaces the pending secret, which is
     * what someone does after scanning into the wrong app.
     */
    @Transactional
    fun beginEnrolment(userId: UUID): MfaEnrolment {
        val user = load(userId)

        if (user.mfaEnabled) {
            throw BusinessRuleException(
                code = "MFA_ALREADY_ENABLED",
                message = "Two-factor authentication is already on for this account",
            )
        }

        val secret = totp.generateSecret()
        user.mfaSecretEnc = cipher.encrypt(secret)
        users.save(user)

        return MfaEnrolment(
            secret = secret,
            provisioningUri = totp.provisioningUri(secret, user.username, issuerName),
        )
    }

    /**
     * Completes enrolment once the user proves their app is working.
     *
     * Returns the recovery codes. They are shown exactly once — this is the only moment the
     * plaintext exists, and the response is the only copy the user will ever get.
     */
    @Transactional
    fun confirmEnrolment(
        userId: UUID,
        code: String,
    ): List<String> {
        val user = load(userId)

        if (user.mfaEnabled) {
            throw BusinessRuleException("MFA_ALREADY_ENABLED", "Two-factor authentication is already on")
        }

        val secret =
            decryptSecret(user)
                ?: throw BusinessRuleException(
                    "MFA_NOT_STARTED",
                    "Start enrolment before confirming it",
                )

        recordAttempt(userId)
        if (!totp.isValid(secret, code)) {
            throw UnauthenticatedException(ErrorCode.MFA_INVALID_CODE, "That code is not correct")
        }

        val generated = recoveryCodes.generate()
        user.mfaEnabled = true
        user.mfaRecoveryCodesEnc = cipher.encrypt(generated.hashes.joinToString("\n"))
        users.save(user)
        clearAttempts(userId)

        log.info("MFA enabled for user {}", userId)
        return generated.plaintext.map(recoveryCodes::forDisplay)
    }

    // ------------------------------------------------------------------------
    // Verification at sign-in
    // ------------------------------------------------------------------------

    /**
     * Checks a code presented against an MFA challenge.
     *
     * Accepts a TOTP code or a recovery code — the user cannot always tell you which they have,
     * and requiring them to say so adds a decision at the worst moment. A recovery code is
     * consumed on use.
     *
     * @return how the second factor was satisfied, for the audit trail.
     */
    @Transactional
    fun verify(
        userId: UUID,
        code: String,
    ): MfaMethod {
        val user = load(userId)

        if (!user.mfaEnabled) {
            // Not an error the caller can act on, and saying so would confirm the account exists
            // and its MFA state to anyone holding a challenge token.
            throw UnauthenticatedException(ErrorCode.MFA_INVALID_CODE, "That code is not correct")
        }

        recordAttempt(userId)

        val secret = decryptSecret(user)
        if (secret != null && totp.isValid(secret, code)) {
            clearAttempts(userId)
            return MfaMethod.TOTP
        }

        val remaining = decryptRecoveryHashes(user)
        val afterUse = recoveryCodes.consume(code, remaining)
        if (afterUse != null) {
            user.mfaRecoveryCodesEnc = cipher.encrypt(afterUse.joinToString("\n"))
            users.save(user)
            clearAttempts(userId)

            // Worth a warning rather than an info: a recovery code being used means the user has
            // lost their authenticator, and running out entirely means the next loss is a support
            // call. Both are worth seeing before they become urgent.
            log.warn("Recovery code used for user {}; {} remaining", userId, afterUse.size)
            return MfaMethod.RECOVERY_CODE
        }

        throw UnauthenticatedException(ErrorCode.MFA_INVALID_CODE, "That code is not correct")
    }

    // ------------------------------------------------------------------------
    // Management
    // ------------------------------------------------------------------------

    /**
     * Turns MFA off. Requires a current code — possession, not just a live session.
     *
     * Without that requirement, an attacker who borrowed an unlocked laptop could disable the
     * second factor from the settings screen, which makes the factor decorative.
     */
    @Transactional
    fun disable(
        userId: UUID,
        code: String,
    ) {
        verify(userId, code)

        val user = load(userId)
        user.mfaEnabled = false
        user.mfaSecretEnc = null
        user.mfaRecoveryCodesEnc = null
        users.save(user)

        log.info("MFA disabled for user {}", userId)
    }

    /** Issues a fresh set, invalidating the old. Requires a current code, for the same reason. */
    @Transactional
    fun regenerateRecoveryCodes(
        userId: UUID,
        code: String,
    ): List<String> {
        verify(userId, code)

        val user = load(userId)
        val generated = recoveryCodes.generate()
        user.mfaRecoveryCodesEnc = cipher.encrypt(generated.hashes.joinToString("\n"))
        users.save(user)

        return generated.plaintext.map(recoveryCodes::forDisplay)
    }

    @Transactional(readOnly = true)
    fun status(userId: UUID): MfaStatus {
        val user = load(userId)
        return MfaStatus(
            enabled = user.mfaEnabled,
            enrolmentPending = !user.mfaEnabled && user.mfaSecretEnc != null,
            recoveryCodesRemaining = if (user.mfaEnabled) decryptRecoveryHashes(user).size else 0,
        )
    }

    // ------------------------------------------------------------------------

    private fun load(userId: UUID): AppUser =
        users.findById(userId).orElseThrow { NotFoundException(message = "No such user") }

    /**
     * A secret we cannot decrypt is treated as absent rather than raising.
     *
     * This is the one place [FieldCipher]'s fail-loud contract is deliberately softened. An
     * undecryptable secret means the key has changed; raising would make every affected user's
     * login return 500 with no route forward, whereas falling through lets the recovery-code path
     * still work. The condition is logged at error, because it needs someone's attention.
     */
    private fun decryptSecret(user: AppUser): String? =
        try {
            cipher.decrypt(user.mfaSecretEnc)
        } catch (e: Exception) {
            log.error("Could not decrypt the MFA secret for user {}", user.id, e)
            null
        }

    private fun decryptRecoveryHashes(user: AppUser): List<String> =
        try {
            cipher.decrypt(user.mfaRecoveryCodesEnc)
                ?.lineSequence()
                ?.filter { it.isNotBlank() }
                ?.toList()
                .orEmpty()
        } catch (e: Exception) {
            log.error("Could not decrypt recovery codes for user {}", user.id, e)
            emptyList()
        }

    private fun recordAttempt(userId: UUID) {
        val key = AttemptKey(TenantContext.currentId(), userId)
        val now = Instant.now()
        val current = attempts.compute(key) { _, existing ->
            if (existing == null || Duration.between(existing.windowStart, now) > ATTEMPT_WINDOW) {
                Attempts(1, now)
            } else {
                Attempts(existing.count + 1, existing.windowStart)
            }
        }!!

        if (current.count > MAX_ATTEMPTS) {
            val remaining = ATTEMPT_WINDOW.minus(Duration.between(current.windowStart, now))
            log.warn("MFA verification rate-limited for user {}", userId)
            throw RateLimitedException(
                retryAfterSeconds = remaining.seconds.coerceAtLeast(1),
                message = "Too many verification attempts. Wait before trying again.",
            )
        }
    }

    private fun clearAttempts(userId: UUID) {
        TenantContext.currentIdOrNull()?.let { attempts.remove(AttemptKey(it, userId)) }
    }

    private data class AttemptKey(val tenantId: UUID, val userId: UUID)

    private data class Attempts(val count: Int, val windowStart: Instant)

    private companion object {
        const val MAX_ATTEMPTS = 5
        val ATTEMPT_WINDOW: Duration = Duration.ofMinutes(5)
    }
}

data class MfaEnrolment(
    /** Shown as text beneath the QR code, for a user whose camera will not focus. */
    val secret: String,
    val provisioningUri: String,
)

data class MfaStatus(
    val enabled: Boolean,
    val enrolmentPending: Boolean,
    val recoveryCodesRemaining: Int,
)

enum class MfaMethod { TOTP, RECOVERY_CODE }

/** Maps onto [LoginMethod] for the audit trail. */
fun MfaMethod.asLoginMethod(): LoginMethod = LoginMethod.MFA
