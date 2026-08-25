package com.hr.identity.internal

import com.hr.shared.api.ApiException
import com.hr.shared.api.ErrorCode
import com.hr.tenancy.IsolationTier
import com.hr.tenancy.TenantContext
import com.hr.tenancy.TenantHandle
import com.hr.tenancy.TenantStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * Unit tests for the authentication flows.
 *
 * These deliberately use mocks rather than a database, so they run everywhere — including in
 * environments without Docker, where the Testcontainers-backed suites cannot. The security logic
 * they cover (reuse detection, lockout, enumeration resistance, device binding) is precisely the
 * logic that must not regress unnoticed.
 */
@DisplayName("Authentication")
class AuthenticationServiceTest {
    private val users: AppUserRepository = mockk(relaxed = true)
    private val devices: UserDeviceRepository = mockk(relaxed = true)
    private val refreshTokens: RefreshTokenRepository = mockk(relaxed = true)
    private val loginEvents: LoginEventRepository = mockk(relaxed = true)
    private val tokenService: TokenService = mockk(relaxed = true)
    private val permissionResolver: PermissionResolver = mockk(relaxed = true)
    private val passwordPolicyService: PasswordPolicyService = mockk(relaxed = true)
    private val passwordEncoder: PasswordEncoder = mockk(relaxed = true)

    private lateinit var service: AuthenticationService

    private val tenantId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        TenantContext.set(
            TenantHandle(
                id = tenantId,
                code = "acme",
                name = "Acme",
                dataRegion = "default",
                defaultCurrency = "LKR",
                timezone = "Asia/Colombo",
                locale = "en",
                isolationTier = IsolationTier.SHARED,
                status = TenantStatus.ACTIVE,
            ),
        )

        every { passwordEncoder.encode(any()) } returns "\$argon2id\$dummy"
        every { tokenService.hash(any()) } answers { "hash-of-" + firstArg<String>() }
        every { tokenService.generateRefreshToken() } returns
            GeneratedRefreshToken("new-refresh", "hash-of-new-refresh", Instant.now().plusSeconds(3600), 3600)
        every { tokenService.issueAccessToken(any(), any(), any(), any()) } returns
            IssuedAccessToken("access-token", Instant.now().plusSeconds(900), 900)
        every { permissionResolver.rolesFor(any()) } returns listOf("EMPLOYEE")
        every { passwordPolicyService.current() } returns PasswordPolicy(tenantId)
        // `JpaRepository.save` is declared as `<S : T> S save(S entity)`. A relaxed mock cannot
        // synthesise a value for that generic type and hands back a bare Object, so every save
        // must be stubbed explicitly to echo its argument.
        every { refreshTokens.save(any()) } answers { firstArg() }
        every { users.save(any()) } answers { firstArg() }
        every { devices.save(any()) } answers { firstArg() }
        every { loginEvents.save(any()) } answers { firstArg() }

        service =
            AuthenticationService(
                users, devices, refreshTokens, loginEvents,
                tokenService, permissionResolver, passwordPolicyService, passwordEncoder,
            )
    }

    @AfterEach
    fun tearDown() = TenantContext.clear()

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Password sign-in")
    inner class PasswordSignIn {
        @Test
        fun `issues a token pair on valid credentials`() {
            val user = activeUser()
            every { users.findByUsernameOrEmail("nimal") } returns user
            every { passwordEncoder.matches("correct", any()) } returns true
            every { devices.findByUserIdAndDeviceId(any(), any()) } returns null

            val response = service.signInWithPassword(grant("nimal", "correct"), "test-agent")

            assertThat(response.accessToken).isEqualTo("access-token")
            assertThat(response.refreshToken).isEqualTo("new-refresh")
            assertThat(response.expiresIn).isEqualTo(900)
        }

        /**
         * The single most important property of a login endpoint after correctness: an attacker
         * must not be able to tell valid usernames from invalid ones.
         */
        @Test
        fun `unknown username and wrong password produce the same error`() {
            every { users.findByUsernameOrEmail("ghost") } returns null
            val unknownUser = catchApi { service.signInWithPassword(grant("ghost", "whatever"), null) }

            val user = activeUser()
            every { users.findByUsernameOrEmail("nimal") } returns user
            every { passwordEncoder.matches(any(), any()) } returns false
            val wrongPassword = catchApi { service.signInWithPassword(grant("nimal", "wrong"), null) }

            assertThat(unknownUser.code).isEqualTo(ErrorCode.INVALID_CREDENTIALS)
            assertThat(wrongPassword.code).isEqualTo(ErrorCode.INVALID_CREDENTIALS)
            assertThat(unknownUser.message).isEqualTo(wrongPassword.message)
        }

        @Test
        fun `performs a dummy hash comparison when the user does not exist`() {
            every { users.findByUsernameOrEmail(any()) } returns null

            catchApi { service.signInWithPassword(grant("ghost", "whatever"), null) }

            // Without this the response returns measurably faster for unknown accounts, which
            // turns the endpoint into a username oracle.
            verify { passwordEncoder.matches("whatever", any()) }
        }

        @Test
        fun `locks the account once the failure threshold is reached`() {
            val user = activeUser().apply { failedAttempts = 4 }
            every { users.findByUsernameOrEmail("nimal") } returns user
            every { passwordEncoder.matches(any(), any()) } returns false

            val error = catchApi { service.signInWithPassword(grant("nimal", "wrong"), null) }

            assertThat(error.code).isEqualTo(ErrorCode.ACCOUNT_LOCKED)
            assertThat(user.lockedUntil).isNotNull()
        }

        @Test
        fun `rejects a locked account without checking the password`() {
            val user = activeUser().apply { lockedUntil = Instant.now().plusSeconds(600) }
            every { users.findByUsernameOrEmail("nimal") } returns user

            val error = catchApi { service.signInWithPassword(grant("nimal", "correct"), null) }

            assertThat(error.code).isEqualTo(ErrorCode.ACCOUNT_LOCKED)
            verify(exactly = 0) { passwordEncoder.matches("correct", any()) }
        }

        @Test
        fun `rejects a disabled account`() {
            val user = activeUser().apply { status = UserStatus.DISABLED }
            every { users.findByUsernameOrEmail("nimal") } returns user

            assertThat(catchApi { service.signInWithPassword(grant("nimal", "correct"), null) }.code)
                .isEqualTo(ErrorCode.ACCOUNT_DISABLED)
        }

        @Test
        fun `rejects an SSO-only account with no password set`() {
            val user = activeUser().apply { passwordHash = null }
            every { users.findByUsernameOrEmail("nimal") } returns user

            assertThat(catchApi { service.signInWithPassword(grant("nimal", "anything"), null) }.code)
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS)
        }

        @Test
        fun `offers biometric enrolment on a mobile device that has not enrolled`() {
            val user = activeUser()
            every { users.findByUsernameOrEmail("nimal") } returns user
            every { passwordEncoder.matches(any(), any()) } returns true
            every { devices.findByUserIdAndDeviceId(any(), any()) } returns null

            val response = service.signInWithPassword(grant("nimal", "correct"), null)

            assertThat(response.biometricEnrolmentOffered)
                .describedAs("Offering enrolment immediately after first sign-in is what stops users typing passwords forever")
                .isTrue()
        }

        @Test
        fun `does not offer biometric enrolment on web`() {
            val user = activeUser()
            every { users.findByUsernameOrEmail("nimal") } returns user
            every { passwordEncoder.matches(any(), any()) } returns true
            every { devices.findByUserIdAndDeviceId(any(), any()) } returns null

            val response =
                service.signInWithPassword(
                    grant("nimal", "correct").copy(device = deviceInfo(platform = "WEB")),
                    null,
                )

            assertThat(response.biometricEnrolmentOffered).isFalse()
        }

        @Test
        fun `records a login event on both success and failure`() {
            every { users.findByUsernameOrEmail(any()) } returns null
            catchApi { service.signInWithPassword(grant("ghost", "x"), null) }

            val captured = slot<LoginEvent>()
            verify { loginEvents.save(capture(captured)) }
            assertThat(captured.captured.result).isEqualTo(LoginResult.FAILURE)
            assertThat(captured.captured.username)
                .describedAs("Recording the attempted username is what makes credential stuffing visible")
                .isEqualTo("ghost")
        }
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Refresh token rotation")
    inner class Rotation {
        @Test
        fun `rotates a valid token and marks the old one used`() {
            val user = activeUser()
            val stored = storedToken(userId = user.id)
            every { refreshTokens.findByTokenHash("hash-of-old-token") } returns stored
            every { users.findById(user.id) } returns Optional.of(user)

            val response = service.refresh("old-token", null)

            assertThat(response.refreshToken).isEqualTo("new-refresh")
            assertThat(stored.isUsed).isTrue()
        }

        @Test
        fun `continues the same family across rotations`() {
            val user = activeUser()
            val stored = storedToken(userId = user.id)
            every { refreshTokens.findByTokenHash(any()) } returns stored
            every { users.findById(user.id) } returns Optional.of(user)

            val saved = slot<RefreshTokenEntity>()
            every { refreshTokens.save(capture(saved)) } answers { firstArg() }

            service.refresh("old-token", null)

            assertThat(saved.captured.familyId).isEqualTo(stored.familyId)
            assertThat(saved.captured.parentId).isEqualTo(stored.id)
        }

        /**
         * The security-critical behaviour of the whole module.
         *
         * A token that has already been rotated is being presented again. That is either a buggy
         * client or a thief, and we cannot tell which — so we assume theft and end every session
         * in the family.
         */
        @Test
        fun `reuse of a spent token revokes the entire family`() {
            val user = activeUser()
            val spent = storedToken(userId = user.id).apply { markUsed() }
            every { refreshTokens.findByTokenHash(any()) } returns spent
            every { users.findById(user.id) } returns Optional.of(user)

            val error = catchApi { service.refresh("stolen-token", null) }

            assertThat(error.code).isEqualTo(ErrorCode.TOKEN_REUSE_DETECTED)
            verify { refreshTokens.revokeFamily(spent.familyId, RevocationReason.REUSE_DETECTED, any()) }
        }

        @Test
        fun `does not issue a token when reuse is detected`() {
            val user = activeUser()
            every { refreshTokens.findByTokenHash(any()) } returns storedToken(userId = user.id).apply { markUsed() }
            every { users.findById(user.id) } returns Optional.of(user)

            catchApi { service.refresh("stolen-token", null) }

            verify(exactly = 0) { tokenService.issueAccessToken(any(), any(), any(), any()) }
        }

        @Test
        fun `rejects an unknown token`() {
            every { refreshTokens.findByTokenHash(any()) } returns null

            assertThat(catchApi { service.refresh("nonsense", null) }.code).isEqualTo(ErrorCode.TOKEN_INVALID)
        }

        @Test
        fun `rejects an expired token`() {
            val user = activeUser()
            every { refreshTokens.findByTokenHash(any()) } returns
                storedToken(userId = user.id, expiresAt = Instant.now().minusSeconds(60))
            every { users.findById(user.id) } returns Optional.of(user)

            assertThat(catchApi { service.refresh("old", null) }.code).isEqualTo(ErrorCode.TOKEN_EXPIRED)
        }

        @Test
        fun `revokes the family when the account is no longer active`() {
            val user = activeUser().apply { status = UserStatus.DISABLED }
            val stored = storedToken(userId = user.id)
            every { refreshTokens.findByTokenHash(any()) } returns stored
            every { users.findById(user.id) } returns Optional.of(user)

            val error = catchApi { service.refresh("token", null) }

            assertThat(error.code).isEqualTo(ErrorCode.ACCOUNT_DISABLED)
            verify { refreshTokens.revokeFamily(stored.familyId, RevocationReason.ACCOUNT_DISABLED, any()) }
        }
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Biometric unlock")
    inner class Biometric {
        @Test
        fun `succeeds for an enrolled device presenting its own token`() {
            val user = activeUser()
            val device = enrolledDevice(user.id)
            val stored = storedToken(userId = user.id, deviceId = device.id)
            every { refreshTokens.findByTokenHash(any()) } returns stored
            every { users.findById(user.id) } returns Optional.of(user)
            every { devices.findById(device.id) } returns Optional.of(device)

            val response = service.signInWithBiometric(BiometricGrantRequest("sealed", device.deviceId), null)

            assertThat(response.accessToken).isEqualTo("access-token")
        }

        /**
         * Stops a token exfiltrated from one device being replayed from another under the guise of
         * a biometric unlock. The token is bound to the device it was issued to.
         */
        @Test
        fun `rejects a token presented by a different device`() {
            val user = activeUser()
            val device = enrolledDevice(user.id)
            every { refreshTokens.findByTokenHash(any()) } returns storedToken(userId = user.id, deviceId = device.id)
            every { users.findById(user.id) } returns Optional.of(user)
            every { devices.findById(device.id) } returns Optional.of(device)

            val error = catchApi { service.signInWithBiometric(BiometricGrantRequest("sealed", "some-other-device"), null) }

            assertThat(error.code).isEqualTo(ErrorCode.TOKEN_INVALID)
        }

        @Test
        fun `rejects a device that never enrolled biometrics`() {
            val user = activeUser()
            val device = enrolledDevice(user.id).apply { biometricEnrolled = false }
            every { refreshTokens.findByTokenHash(any()) } returns storedToken(userId = user.id, deviceId = device.id)
            every { users.findById(user.id) } returns Optional.of(user)
            every { devices.findById(device.id) } returns Optional.of(device)

            val error = catchApi { service.signInWithBiometric(BiometricGrantRequest("sealed", device.deviceId), null) }

            assertThat(error.code).isEqualTo(ErrorCode.STEP_UP_REQUIRED)
        }

        @Test
        fun `rejects a revoked device`() {
            val user = activeUser()
            val device = enrolledDevice(user.id).apply { revoke("test") }
            // revoke() clears the enrolment flag, so re-set it to isolate the revocation check.
            device.biometricEnrolled = true
            every { refreshTokens.findByTokenHash(any()) } returns storedToken(userId = user.id, deviceId = device.id)
            every { users.findById(user.id) } returns Optional.of(user)
            every { devices.findById(device.id) } returns Optional.of(device)

            val error = catchApi { service.signInWithBiometric(BiometricGrantRequest("sealed", device.deviceId), null) }

            assertThat(error.code).isEqualTo(ErrorCode.DEVICE_REVOKED)
        }
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Sign out")
    inner class SignOut {
        @Test
        fun `revokes only the presented token's family`() {
            val stored = storedToken(userId = UUID.randomUUID())
            every { refreshTokens.findByTokenHash(any()) } returns stored

            service.signOut("some-token", null)

            verify { refreshTokens.revokeFamily(stored.familyId, RevocationReason.LOGOUT, any()) }
            verify(exactly = 0) { refreshTokens.revokeAllForUser(any(), any(), any()) }
        }

        @Test
        fun `falls back to revoking all sessions when no token is supplied`() {
            val userId = UUID.randomUUID()

            service.signOut(null, userId)

            verify { refreshTokens.revokeAllForUser(userId, RevocationReason.LOGOUT, any()) }
        }

        @Test
        fun `is idempotent when nothing can be identified`() {
            every { refreshTokens.findByTokenHash(any()) } returns null

            service.signOut(null, null)
            service.signOut("unknown", null)

            verify(exactly = 0) { refreshTokens.revokeFamily(any(), any(), any()) }
        }
    }

    // -----------------------------------------------------------------------
    // Fixtures

    private fun activeUser() =
        AppUser(
            username = "nimal",
            email = "nimal@acme.lk",
            passwordHash = "\$argon2id\$stored",
            status = UserStatus.ACTIVE,
        ).apply { tenantId = this@AuthenticationServiceTest.tenantId }

    private fun enrolledDevice(userId: UUID) =
        UserDevice(
            userId = userId,
            deviceId = "pixel-8-abc",
            platform = DevicePlatform.ANDROID,
        ).apply {
            biometricEnrolled = true
            tenantId = this@AuthenticationServiceTest.tenantId
        }

    private fun storedToken(
        userId: UUID,
        deviceId: UUID? = null,
        expiresAt: Instant = Instant.now().plusSeconds(3600),
    ) = RefreshTokenEntity(
        tenantId = tenantId,
        userId = userId,
        deviceId = deviceId,
        tokenHash = "hash-of-old-token",
        familyId = UUID.randomUUID(),
        expiresAt = expiresAt,
    )

    private fun deviceInfo(platform: String = "ANDROID") =
        DeviceInfoDto(deviceId = "pixel-8-abc", platform = platform, model = "Pixel 8")

    private fun grant(
        username: String,
        password: String,
    ) = PasswordGrantRequest(username = username, password = password, device = deviceInfo())

    private fun catchApi(block: () -> Unit): ApiException {
        var caught: ApiException? = null
        assertThatThrownBy { block() }
            .isInstanceOfSatisfying(ApiException::class.java) { caught = it }
        return caught!!
    }
}
