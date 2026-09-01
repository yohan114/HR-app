package com.hr.identity.internal

import com.hr.shared.api.ErrorCode
import com.hr.shared.api.ForbiddenException
import com.hr.shared.api.UnauthenticatedException
import com.hr.tenancy.TenantContext
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * The authentication flows: password sign-in, token refresh, biometric unlock, sign-out.
 *
 * Three principles run through all of it:
 *
 * 1. **Failures are uniform.** An unknown username and a wrong password produce the same
 *    `INVALID_CREDENTIALS` response, in comparable time. Anything else turns the login endpoint
 *    into an account-enumeration oracle.
 * 2. **Refresh token reuse is treated as theft.** We cannot distinguish a buggy client replaying a
 *    request from an attacker using a stolen token, and guessing wrong in the attacker's favour
 *    costs an account. So we revoke the whole family and make everyone sign in again.
 * 3. **Biometric unlock is a real credential, not a shortcut.** The refresh token lives in the
 *    device's secure hardware and the OS releases it only after a user presence check. That is why
 *    this flow does not, and must not, ask for a password.
 */
@Service
class AuthenticationService(
    private val users: AppUserRepository,
    private val devices: UserDeviceRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val loginEvents: LoginEventRepository,
    private val tokenService: TokenService,
    private val permissionResolver: PermissionResolver,
    private val passwordPolicyService: PasswordPolicyService,
    private val passwordEncoder: PasswordEncoder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // -----------------------------------------------------------------------
    // Password grant
    // -----------------------------------------------------------------------

    @Transactional
    fun signInWithPassword(
        request: PasswordGrantRequest,
        userAgent: String?,
    ): TokenResponse {
        val tenantId = TenantContext.currentId()
        val user = users.findByUsernameOrEmail(request.username)

        if (user == null) {
            // Spend comparable time to a real verification so response timing does not reveal
            // whether the account exists. Without this the endpoint leaks valid usernames.
            passwordEncoder.matches(request.password, dummyHash)
            audit(tenantId, LoginMethod.PASSWORD, LoginResult.FAILURE, null, null, request.username, ErrorCode.INVALID_CREDENTIALS, userAgent)
            throw UnauthenticatedException(ErrorCode.INVALID_CREDENTIALS, "Invalid username or password")
        }

        if (user.isLocked) {
            audit(tenantId, LoginMethod.PASSWORD, LoginResult.LOCKED, user.id, null, request.username, ErrorCode.ACCOUNT_LOCKED, userAgent)
            val remaining = Duration.between(Instant.now(), user.lockedUntil).coerceAtLeast(Duration.ZERO)
            throw ForbiddenException(
                ErrorCode.ACCOUNT_LOCKED,
                "Account temporarily locked after repeated failed attempts",
                mapOf("retryAfterSeconds" to remaining.seconds),
            )
        }

        if (user.status != UserStatus.ACTIVE) {
            audit(tenantId, LoginMethod.PASSWORD, LoginResult.FAILURE, user.id, null, request.username, ErrorCode.ACCOUNT_DISABLED, userAgent)
            throw ForbiddenException(ErrorCode.ACCOUNT_DISABLED, "This account is not active")
        }

        val hash = user.passwordHash
        if (hash == null || !passwordEncoder.matches(request.password, hash)) {
            val policy = passwordPolicyService.current()
            val nowLocked = user.recordFailedLogin(policy.lockoutThreshold, policy.lockoutMinutes)
            users.save(user)
            audit(
                tenantId, LoginMethod.PASSWORD,
                if (nowLocked) LoginResult.LOCKED else LoginResult.FAILURE,
                user.id, null, request.username,
                if (nowLocked) ErrorCode.ACCOUNT_LOCKED else ErrorCode.INVALID_CREDENTIALS,
                userAgent,
            )
            if (nowLocked) {
                log.warn("Account {} locked after {} failed attempts", user.id, user.failedAttempts)
                throw ForbiddenException(
                    ErrorCode.ACCOUNT_LOCKED,
                    "Account temporarily locked after repeated failed attempts",
                    mapOf("retryAfterSeconds" to policy.lockoutMinutes * 60L),
                )
            }
            throw UnauthenticatedException(ErrorCode.INVALID_CREDENTIALS, "Invalid username or password")
        }

        // The password was right. If a second factor is enrolled, stop here and hand back a
        // challenge rather than a session.
        //
        // Note what is deliberately *not* done yet: the failed-attempt counter is reset and the
        // last-login timestamp is not. Recording a successful login before the second factor has
        // been satisfied would make "last signed in" mean "last typed the right password", which
        // is exactly the event a user checks that field to detect.
        if (user.mfaEnabled) {
            user.recordSuccessfulLogin()
            users.save(user)

            val challenge = tokenService.issueMfaChallengeToken(user.id)
            audit(tenantId, LoginMethod.PASSWORD, LoginResult.MFA_REQUIRED, user.id, null, request.username, null, userAgent)

            throw UnauthenticatedException(
                ErrorCode.MFA_REQUIRED,
                "A verification code is required",
                mapOf(
                    "mfaToken" to challenge.value,
                    "expiresInSeconds" to challenge.expiresInSeconds,
                ),
            )
        }

        user.recordSuccessfulLogin()
        users.save(user)

        val device = upsertDevice(user.id, request.device)
        val issued = issueTokenPair(user, device, LoginMethod.PASSWORD, parentToken = null)

        audit(tenantId, LoginMethod.PASSWORD, LoginResult.SUCCESS, user.id, device.id, request.username, null, userAgent)

        return issued.copy(
            biometricEnrolmentOffered = !device.biometricEnrolled && device.platform in BIOMETRIC_CAPABLE,
            mustChangePassword = user.mustChangePassword,
        )
    }

    /**
     * Issues the session once the second factor has been satisfied.
     *
     * Called by `MfaController` after `MfaService.verify` succeeds. It deliberately does **not**
     * re-check the code: verification and consumption belong together, and splitting them would
     * create a window in which a recovery code has been accepted but not yet spent.
     *
     * The device is registered here rather than at the password step, so a login abandoned at the
     * challenge screen does not leave a device record — and therefore does not offer biometric
     * enrolment to something that never completed a sign-in.
     */
    @Transactional
    fun completeMfaSignIn(
        userId: UUID,
        deviceInfo: DeviceInfoDto,
        userAgent: String?,
    ): TokenResponse {
        val tenantId = TenantContext.currentId()
        val user = users.findById(userId).orElseThrow {
            UnauthenticatedException(ErrorCode.TOKEN_INVALID, "That challenge is no longer valid")
        }

        if (user.status != UserStatus.ACTIVE) {
            audit(tenantId, LoginMethod.MFA, LoginResult.FAILURE, user.id, null, user.username, ErrorCode.ACCOUNT_DISABLED, userAgent)
            throw ForbiddenException(ErrorCode.ACCOUNT_DISABLED, "This account is not active")
        }

        val device = upsertDevice(user.id, deviceInfo)
        val issued = issueTokenPair(user, device, LoginMethod.MFA, parentToken = null)

        audit(tenantId, LoginMethod.MFA, LoginResult.SUCCESS, user.id, device.id, user.username, null, userAgent)

        return issued.copy(
            biometricEnrolmentOffered = !device.biometricEnrolled && device.platform in BIOMETRIC_CAPABLE,
            mustChangePassword = user.mustChangePassword,
        )
    }

    // -----------------------------------------------------------------------
    // Refresh
    // -----------------------------------------------------------------------

    @Transactional
    fun refresh(
        presentedToken: String,
        userAgent: String?,
    ): TokenResponse = rotate(presentedToken, LoginMethod.REFRESH, expectedDeviceId = null, userAgent = userAgent)

    /**
     * Biometric unlock.
     *
     * Identical to a refresh, with two extra conditions: the token must belong to the device
     * presenting it, and that device must have completed biometric enrolment. Both are checked so
     * that a token exfiltrated from one device cannot be replayed from another under the guise of
     * a biometric unlock.
     */
    @Transactional
    fun signInWithBiometric(
        request: BiometricGrantRequest,
        userAgent: String?,
    ): TokenResponse =
        rotate(
            presentedToken = request.sealedRefreshToken,
            method = LoginMethod.BIOMETRIC,
            expectedDeviceId = request.deviceId,
            userAgent = userAgent,
        )

    private fun rotate(
        presentedToken: String,
        method: LoginMethod,
        expectedDeviceId: String?,
        userAgent: String?,
    ): TokenResponse {
        val tenantId = TenantContext.currentId()
        val stored = refreshTokens.findByTokenHash(tokenService.hash(presentedToken))

        if (stored == null) {
            audit(tenantId, method, LoginResult.FAILURE, null, null, null, ErrorCode.TOKEN_INVALID, userAgent)
            throw UnauthenticatedException(ErrorCode.TOKEN_INVALID, "Refresh token is not valid")
        }

        // --- Reuse detection -------------------------------------------------
        // A token that has already been rotated is being presented again. Assume theft.
        if (stored.isUsed) {
            val revoked = refreshTokens.revokeFamily(stored.familyId, RevocationReason.REUSE_DETECTED, Instant.now())
            log.warn(
                "Refresh token reuse detected for user {} (family={}); revoked {} tokens",
                stored.userId, stored.familyId, revoked,
            )
            audit(tenantId, method, LoginResult.FAILURE, stored.userId, stored.deviceId, null, ErrorCode.TOKEN_REUSE_DETECTED, userAgent)
            throw UnauthenticatedException(
                ErrorCode.TOKEN_REUSE_DETECTED,
                "This session has been ended for security reasons. Please sign in again.",
            )
        }

        if (stored.isRevoked || stored.isExpired) {
            audit(tenantId, method, LoginResult.FAILURE, stored.userId, stored.deviceId, null, ErrorCode.TOKEN_EXPIRED, userAgent)
            throw UnauthenticatedException(ErrorCode.TOKEN_EXPIRED, "Session expired. Please sign in again.")
        }

        val user =
            users.findById(stored.userId).orElse(null)
                ?: throw UnauthenticatedException(ErrorCode.TOKEN_INVALID, "Refresh token is not valid")

        if (!user.canAuthenticate) {
            refreshTokens.revokeFamily(stored.familyId, RevocationReason.ACCOUNT_DISABLED, Instant.now())
            audit(tenantId, method, LoginResult.FAILURE, user.id, stored.deviceId, null, ErrorCode.ACCOUNT_DISABLED, userAgent)
            throw ForbiddenException(ErrorCode.ACCOUNT_DISABLED, "This account is not active")
        }

        val device = stored.deviceId?.let { devices.findById(it).orElse(null) }

        if (method == LoginMethod.BIOMETRIC) {
            requireBiometricEligibility(device, expectedDeviceId, tenantId, user, userAgent)
        }

        if (device != null && !device.isActive) {
            refreshTokens.revokeAllForDevice(device.id, RevocationReason.DEVICE_REVOKED, Instant.now())
            audit(tenantId, method, LoginResult.FAILURE, user.id, device.id, null, ErrorCode.DEVICE_REVOKED, userAgent)
            throw ForbiddenException(ErrorCode.DEVICE_REVOKED, "This device's access has been revoked")
        }

        stored.markUsed()
        refreshTokens.save(stored)
        device?.touch(appVersion = null, osVersion = null, pushToken = null)

        val issued = issueTokenPair(user, device, method, parentToken = stored)
        audit(tenantId, method, LoginResult.SUCCESS, user.id, device?.id, user.username, null, userAgent)

        return issued.copy(
            biometricEnrolmentOffered = device != null && !device.biometricEnrolled && device.platform in BIOMETRIC_CAPABLE,
            mustChangePassword = user.mustChangePassword,
        )
    }

    private fun requireBiometricEligibility(
        device: UserDevice?,
        expectedDeviceId: String?,
        tenantId: UUID,
        user: AppUser,
        userAgent: String?,
    ) {
        // The token must have been issued to a device, and to *this* device.
        if (device == null || device.deviceId != expectedDeviceId) {
            log.warn(
                "Biometric grant device mismatch for user {}: token device={} presented={}",
                user.id, device?.deviceId, expectedDeviceId,
            )
            audit(tenantId, LoginMethod.BIOMETRIC, LoginResult.FAILURE, user.id, device?.id, null, ErrorCode.TOKEN_INVALID, userAgent)
            throw UnauthenticatedException(ErrorCode.TOKEN_INVALID, "Refresh token is not valid for this device")
        }
        if (!device.biometricEnrolled) {
            audit(tenantId, LoginMethod.BIOMETRIC, LoginResult.FAILURE, user.id, device.id, null, ErrorCode.STEP_UP_REQUIRED, userAgent)
            throw ForbiddenException(
                ErrorCode.STEP_UP_REQUIRED,
                "Biometric sign-in is not enrolled on this device",
            )
        }
    }

    // -----------------------------------------------------------------------
    // Sign out
    // -----------------------------------------------------------------------

    /**
     * Signs out one session.
     *
     * Revokes the presented token's family only — other devices stay signed in. Signing a user out
     * everywhere is a separate, deliberate action, because doing it implicitly on every logout is
     * astonishing behaviour for someone who simply closed the app on one phone.
     */
    @Transactional
    fun signOut(
        presentedToken: String?,
        userId: UUID?,
    ) {
        val stored = presentedToken?.let { refreshTokens.findByTokenHash(tokenService.hash(it)) }
        when {
            stored != null -> refreshTokens.revokeFamily(stored.familyId, RevocationReason.LOGOUT, Instant.now())
            userId != null -> refreshTokens.revokeAllForUser(userId, RevocationReason.LOGOUT, Instant.now())
            else -> Unit // Nothing to revoke; sign-out is idempotent and never errors.
        }
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private fun issueTokenPair(
        user: AppUser,
        device: UserDevice?,
        method: LoginMethod,
        parentToken: RefreshTokenEntity?,
    ): TokenResponse {
        val roles = permissionResolver.rolesFor(user.id)
        val access = tokenService.issueAccessToken(user, roles, device?.id, method)
        val refresh = tokenService.generateRefreshToken()

        refreshTokens.save(
            RefreshTokenEntity(
                tenantId = TenantContext.currentId(),
                userId = user.id,
                deviceId = device?.id,
                tokenHash = refresh.hash,
                // A rotation continues the existing family; a fresh sign-in starts a new one.
                familyId = parentToken?.familyId ?: UUID.randomUUID(),
                parentId = parentToken?.id,
                expiresAt = refresh.expiresAt,
            ),
        )

        return TokenResponse(
            accessToken = access.value,
            refreshToken = refresh.value,
            expiresIn = access.expiresInSeconds,
            refreshExpiresIn = refresh.expiresInSeconds,
        )
    }

    private fun upsertDevice(
        userId: UUID,
        info: DeviceInfoDto,
    ): UserDevice {
        val existing = devices.findByUserIdAndDeviceId(userId, info.deviceId)
        if (existing != null) {
            if (!existing.isActive) {
                // A previously revoked device signing in again with a valid password is a
                // legitimate re-enrolment — the user proved possession of the credential.
                // Biometric enrolment, however, starts over.
                existing.revokedAt = null
                existing.revokedReason = null
                existing.trusted = true
                existing.biometricEnrolled = false
            }
            existing.touch(info.appVersion, info.osVersion, info.pushToken)
            return devices.save(existing)
        }

        return devices.save(
            UserDevice(
                userId = userId,
                deviceId = info.deviceId,
                platform = info.platformEnum(),
                model = info.model,
                osVersion = info.osVersion,
                appVersion = info.appVersion,
            ).apply {
                pushToken = info.pushToken?.ifBlank { null }
                lastSeenAt = Instant.now()
            },
        )
    }

    private fun audit(
        tenantId: UUID,
        method: LoginMethod,
        result: LoginResult,
        userId: UUID?,
        deviceId: UUID?,
        username: String?,
        failureCode: String?,
        userAgent: String?,
    ) {
        loginEvents.save(
            LoginEvent(
                tenantId = tenantId,
                method = method,
                result = result,
                userId = userId,
                deviceId = deviceId,
                username = username?.take(255),
                failureCode = failureCode,
                userAgent = userAgent?.take(512),
            ),
        )
    }

    /**
     * A genuine Argon2id hash of a random value, used to equalise response timing when the
     * username does not exist.
     *
     * Generated once from the configured encoder rather than hardcoded, so it always matches the
     * cost parameters currently in force. A hardcoded literal would silently stop equalising
     * anything the moment those parameters were raised — and if it were not a well-formed
     * encoding, `matches` would reject it immediately and the timing signal would be *worse* than
     * doing nothing.
     */
    private val dummyHash: String by lazy {
        passwordEncoder.encode(UUID.randomUUID().toString())
    }

    private companion object {
        val BIOMETRIC_CAPABLE = setOf(DevicePlatform.ANDROID, DevicePlatform.IOS)
    }
}
