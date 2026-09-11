package com.hr.identity

import com.hr.identity.internal.CreateRolePayloadDto
import com.hr.identity.internal.PermissionResolver
import com.hr.identity.internal.RoleAdminService
import com.hr.shared.api.BadRequestException
import com.hr.shared.api.ConflictException
import com.hr.tenancy.IsolationTier
import com.hr.tenancy.TenantContext
import com.hr.tenancy.TenantHandle
import com.hr.tenancy.TenantStatus
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.util.UUID

@DisplayName("Role administration service")
class RoleAdminServiceTest {
    private val jdbc = mockk<JdbcTemplate>(relaxed = true)
    private val permissionResolver = mockk<PermissionResolver>(relaxed = true)
    private val tenantId = UUID.randomUUID()

    private lateinit var service: RoleAdminService

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
        service = RoleAdminService(jdbc, permissionResolver)
    }

    @AfterEach
    fun tearDown() {
        TenantContext.clear()
    }

    @Test
    fun `createRole rejects duplicate code`() {
        every {
            jdbc.queryForObject(
                any<String>(),
                Int::class.java,
                tenantId,
                "ADMIN",
            )
        } returns 1

        val payload = CreateRolePayloadDto(name = "Admin Clone", code = "ADMIN")
        assertThatThrownBy { service.createRole(payload) }
            .isInstanceOf(ConflictException::class.java)
    }

    @Test
    fun `deleteRole rejects deleting system role`() {
        val roleId = UUID.randomUUID()

        every {
            jdbc.query(
                match { it.contains("FROM role r") && it.contains("r.id = ?") },
                any<RowMapper<Any>>(),
                tenantId,
                roleId,
            )
        } answers {
            val mapper = secondArg<RowMapper<Any>>()
            val rs = mockk<java.sql.ResultSet>(relaxed = true) {
                every { getObject("id", UUID::class.java) } returns roleId
                every { getString("key") } returns "ADMIN"
                every { getString("name") } returns "Administrator"
                every { getString("description") } returns "System admin"
                every { getBoolean("is_system") } returns true
                every { getInt("assigned_count") } returns 0
            }
            listOf(mapper.mapRow(rs, 1)!!)
        }

        assertThatThrownBy { service.deleteRole(roleId) }
            .isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("System roles cannot be deleted")
    }
}
