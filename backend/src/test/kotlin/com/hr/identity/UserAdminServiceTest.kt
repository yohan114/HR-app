package com.hr.identity

import com.hr.identity.internal.AppUser
import com.hr.identity.internal.AppUserRepository
import com.hr.identity.internal.CreateUserPayloadDto
import com.hr.identity.internal.PermissionResolver
import com.hr.identity.internal.RefreshTokenRepository
import com.hr.identity.internal.ResetPasswordPayloadDto
import com.hr.identity.internal.UserAdminService
import com.hr.identity.internal.UserDeviceRepository
import com.hr.shared.api.ConflictException
import com.hr.tenancy.IsolationTier
import com.hr.tenancy.TenantContext
import com.hr.tenancy.TenantHandle
import com.hr.tenancy.TenantStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional
import java.util.UUID

@DisplayName("User administration service")
class UserAdminServiceTest {
    private val appUserRepository = mockk<AppUserRepository>()
    private val deviceRepository = mockk<UserDeviceRepository>()
    private val refreshTokenRepository = mockk<RefreshTokenRepository>(relaxed = true)
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val permissionResolver = mockk<PermissionResolver>(relaxed = true)
    private val jdbc = mockk<JdbcTemplate>(relaxed = true)
    private val tenantId = UUID.randomUUID()

    private lateinit var service: UserAdminService

    @BeforeEach
    fun setUp() {
        TenantContext.set(
            TenantHandle(
                id = tenantId,
                code = "test",
                name = "Test Tenant",
                dataRegion = "default",
                defaultCurrency = "USD",
                timezone = "UTC",
                locale = "en",
                isolationTier = IsolationTier.SHARED,
                status = TenantStatus.ACTIVE,
            )
        )
        service = UserAdminService(
            appUserRepository,
            deviceRepository,
            refreshTokenRepository,
            passwordEncoder,
            permissionResolver,
            jdbc,
        )
    }

    @AfterEach
    fun tearDown() {
        TenantContext.clear()
    }

    @Test
    fun `createUser rejects duplicate username`() {
        every { appUserRepository.findByUsername("john") } returns mockk()

        val payload = CreateUserPayloadDto(username = "john")
        assertThatThrownBy { service.createUser(payload) }
            .isInstanceOf(ConflictException::class.java)
    }

    @Test
    fun `resetPassword encodes new password and revokes sessions`() {
        val user = AppUser(username = "jane")
        val userId = user.id

        every { appUserRepository.findById(userId) } returns Optional.of(user)
        every { passwordEncoder.encode("Secret123!") } returns "hashed_secret_password"
        every { appUserRepository.save(user) } returns user

        val response = service.resetPassword(
            userId,
            ResetPasswordPayloadDto(temporaryPassword = "Secret123!", mustChangePassword = true),
        )

        assertThat(response.success).isTrue()
        assertThat(user.passwordHash).isEqualTo("hashed_secret_password")
        assertThat(user.mustChangePassword).isTrue()
        verify { refreshTokenRepository.revokeAllForUser(userId, "PASSWORD_RESET", any()) }
    }
}
