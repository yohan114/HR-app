package com.hr.tenancy

import com.hr.shared.api.ConflictException
import com.hr.shared.api.NotFoundException
import com.hr.tenancy.internal.CreateTenantPayloadDto
import com.hr.tenancy.internal.Tenant
import com.hr.tenancy.internal.TenantAdminService
import com.hr.tenancy.internal.TenantJpaRepository
import com.hr.tenancy.internal.TenantRegistry
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
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.util.Optional
import java.util.UUID

@DisplayName("Tenant administration service")
class TenantAdminServiceTest {
    private val repository = mockk<TenantJpaRepository>()
    private val registry = mockk<TenantRegistry>(relaxed = true)
    private val jdbc = mockk<JdbcTemplate>(relaxed = true)
    private val transactionTemplate = mockk<TransactionTemplate>()

    private lateinit var service: TenantAdminService

    @BeforeEach
    fun setUp() {
        every { transactionTemplate.execute(any<TransactionCallback<Any?>>()) } answers {
            val callback = firstArg<TransactionCallback<Any?>>()
            callback.doInTransaction(mockk(relaxed = true))
        }
        service = TenantAdminService(repository, registry, jdbc, transactionTemplate)
    }

    @AfterEach
    fun tearDown() {
        TenantContext.clear()
    }

    @Test
    fun `listTenants filters by query and status`() {
        val t1 = Tenant(
            code = "acme",
            name = "Acme Corp",
            countryCode = "US",
            defaultCurrency = "USD",
            status = TenantStatus.ACTIVE,
        )

        val t2 = Tenant(
            code = "beta",
            name = "Beta Inc",
            countryCode = "LK",
            defaultCurrency = "LKR",
            status = TenantStatus.SUSPENDED,
        )

        every { repository.findAll() } returns listOf(t1, t2)

        val activeList = service.listTenants(status = "ACTIVE")
        assertThat(activeList).hasSize(1)
        assertThat(activeList[0].code).isEqualTo("acme")

        val queryList = service.listTenants(q = "Beta")
        assertThat(queryList).hasSize(1)
        assertThat(queryList[0].code).isEqualTo("beta")
    }

    @Test
    fun `getTenant throws NotFoundException when tenant is missing`() {
        val id = UUID.randomUUID()
        every { repository.findById(id) } returns Optional.empty()

        assertThatThrownBy { service.getTenant(id) }
            .isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `createTenant rejects duplicate tenant code`() {
        val payload = CreateTenantPayloadDto(
            code = "duplicate",
            name = "Duplicate Corp",
            countryCode = "US",
            defaultCurrency = "USD",
        )
        every { repository.findByCode("duplicate") } returns mockk()

        assertThatThrownBy { service.createTenant(payload) }
            .isInstanceOf(ConflictException::class.java)
    }

    @Test
    fun `archiveTenant transitions status to ARCHIVED`() {
        val tenant = Tenant(
            code = "to-archive",
            name = "Old Corp",
            countryCode = "US",
            defaultCurrency = "USD",
            status = TenantStatus.ACTIVE,
        )
        val id = tenant.id

        every { repository.findById(id) } returns Optional.of(tenant)
        every { repository.save(any()) } answers { firstArg() }

        val archived = service.archiveTenant(id)
        assertThat(archived.status).isEqualTo(TenantStatus.ARCHIVED)
        verify { registry.evict(id) }
    }
}
