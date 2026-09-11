package com.hr.tenancy.internal

import com.hr.shared.api.ConflictException
import com.hr.shared.api.ErrorCode
import com.hr.shared.api.NotFoundException
import com.hr.tenancy.IsolationTier
import com.hr.tenancy.TenantContext
import com.hr.tenancy.TenantStatus
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

@Service
class TenantAdminService(
    private val repository: TenantJpaRepository,
    private val registry: TenantRegistry,
    private val jdbc: JdbcTemplate,
    private val transactionTemplate: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun listTenants(
        q: String? = null,
        status: String? = null,
        plan: String? = null,
    ): List<TenantDetailDto> {
        val allTenants = repository.findAll()
        val filtered = allTenants.filter { tenant ->
            val matchesQ = q.isNullOrBlank() ||
                tenant.name.contains(q, ignoreCase = true) ||
                tenant.code.contains(q, ignoreCase = true) ||
                (tenant.legalName?.contains(q, ignoreCase = true) == true)
            val matchesStatus = status.isNullOrBlank() || tenant.status.name.equals(status, ignoreCase = true)
            val matchesPlan = plan.isNullOrBlank() || tenant.subscriptionPlan.equals(plan, ignoreCase = true)
            matchesQ && matchesStatus && matchesPlan
        }

        return filtered.map { tenant ->
            val modules = getModulesForTenant(tenant)
            val adminEmail = getAdminEmailForTenant(tenant)
            tenant.toDetailDto(modules, adminEmail)
        }
    }

    @Transactional(readOnly = true)
    fun getTenant(id: UUID): TenantDetailDto {
        val tenant = repository.findById(id).orElse(null)
            ?: throw NotFoundException(ErrorCode.NOT_FOUND, "Tenant not found: $id")
        val modules = getModulesForTenant(tenant)
        val adminEmail = getAdminEmailForTenant(tenant)
        return tenant.toDetailDto(modules, adminEmail)
    }

    fun createTenant(payload: CreateTenantPayloadDto): TenantDetailDto {
        val normalisedCode = payload.code.trim().lowercase()
        if (repository.findByCode(normalisedCode) != null) {
            throw ConflictException(ErrorCode.CONFLICT, "Tenant code '$normalisedCode' already exists")
        }

        val newTenant = Tenant(
            code = normalisedCode,
            name = payload.name.trim(),
            legalName = payload.legalName?.trim(),
            countryCode = payload.countryCode.trim().uppercase(),
            timezone = payload.timezone.ifBlank { "UTC" },
            defaultCurrency = payload.defaultCurrency.trim().uppercase(),
            locale = payload.locale.ifBlank { "en" },
            dataRegion = payload.dataRegion.ifBlank { "default" },
            isolationTier = payload.isolationTier,
            status = TenantStatus.ACTIVE,
            subscriptionPlan = payload.subscriptionPlan ?: "ENTERPRISE",
        )

        val saved = TenantContext.runWithoutTenant {
            repository.save(newTenant)
        }

        // Provision defaults and initial modules
        TenantContext.runAs(saved.toHandle()) {
            transactionTemplate.execute {
                jdbc.query("SELECT provision_tenant_defaults(?)", { _, _ -> null }, saved.id)

                payload.modules?.forEach { (moduleKey, enabled) ->
                    jdbc.update(
                        """
                        INSERT INTO tenant_module (tenant_id, module_key, enabled, updated_at)
                        VALUES (?, ?, ?, now())
                        ON CONFLICT (tenant_id, module_key) DO UPDATE
                           SET enabled = EXCLUDED.enabled, updated_at = now()
                        """,
                        saved.id,
                        moduleKey,
                        enabled,
                    )
                }
            }
        }

        registry.evict(saved.id)
        log.info("Created and provisioned tenant {} ({})", saved.code, saved.id)
        val modules = getModulesForTenant(saved)
        return saved.toDetailDto(modules, payload.adminEmail)
    }

    fun updateTenant(id: UUID, payload: UpdateTenantPayloadDto): TenantDetailDto {
        val tenant = repository.findById(id).orElse(null)
            ?: throw NotFoundException(ErrorCode.NOT_FOUND, "Tenant not found: $id")

        payload.name?.let { if (it.isNotBlank()) tenant.name = it.trim() }
        payload.legalName?.let { tenant.legalName = it.trim() }
        payload.timezone?.let { if (it.isNotBlank()) tenant.timezone = it.trim() }
        payload.defaultCurrency?.let { if (it.isNotBlank()) tenant.defaultCurrency = it.trim().uppercase() }
        payload.locale?.let { if (it.isNotBlank()) tenant.locale = it.trim() }
        payload.dataRegion?.let { if (it.isNotBlank()) tenant.dataRegion = it.trim() }
        payload.isolationTier?.let { tenant.isolationTier = it }
        payload.subscriptionPlan?.let { tenant.subscriptionPlan = it }
        payload.status?.let { tenant.status = it }

        val saved = TenantContext.runWithoutTenant {
            repository.save(tenant)
        }
        registry.evict(saved.id)

        val modules = getModulesForTenant(saved)
        val adminEmail = payload.adminEmail ?: getAdminEmailForTenant(saved)
        return saved.toDetailDto(modules, adminEmail)
    }

    fun archiveTenant(id: UUID): TenantDetailDto {
        val tenant = repository.findById(id).orElse(null)
            ?: throw NotFoundException(ErrorCode.NOT_FOUND, "Tenant not found: $id")
        tenant.status = TenantStatus.ARCHIVED
        val saved = TenantContext.runWithoutTenant {
            repository.save(tenant)
        }
        registry.evict(saved.id)
        val modules = getModulesForTenant(saved)
        val adminEmail = getAdminEmailForTenant(saved)
        return saved.toDetailDto(modules, adminEmail)
    }

    fun updateTenantModules(id: UUID, moduleToggles: Map<String, Boolean>): List<TenantModuleStatusDto> {
        val tenant = repository.findById(id).orElse(null)
            ?: throw NotFoundException(ErrorCode.NOT_FOUND, "Tenant not found: $id")

        TenantContext.runAs(tenant.toHandle()) {
            transactionTemplate.execute {
                moduleToggles.forEach { (moduleKey, enabled) ->
                    jdbc.update(
                        """
                        INSERT INTO tenant_module (tenant_id, module_key, enabled, updated_at)
                        VALUES (?, ?, ?, now())
                        ON CONFLICT (tenant_id, module_key) DO UPDATE
                           SET enabled = EXCLUDED.enabled, updated_at = now()
                        """,
                        tenant.id,
                        moduleKey,
                        enabled,
                    )
                }
            }
        }

        return getModulesForTenant(tenant)
    }

    private fun getModulesForTenant(tenant: Tenant): List<TenantModuleStatusDto> =
        TenantContext.runAs(tenant.toHandle()) {
            jdbc.query(
                "SELECT module_key, enabled, updated_at FROM tenant_module WHERE tenant_id = ? ORDER BY module_key",
                { rs, _ ->
                    TenantModuleStatusDto(
                        moduleKey = rs.getString("module_key"),
                        enabled = rs.getBoolean("enabled"),
                        config = emptyMap(),
                        updatedAt = rs.getTimestamp("updated_at")?.toInstant(),
                    )
                },
                tenant.id,
            )
        }

    private fun getAdminEmailForTenant(tenant: Tenant): String? =
        TenantContext.runAs(tenant.toHandle()) {
            jdbc.query(
                "SELECT email FROM app_user WHERE tenant_id = ? AND email IS NOT NULL ORDER BY created_at ASC LIMIT 1",
                { rs, _ -> rs.getString("email") },
                tenant.id,
            ).firstOrNull()
        }

    private fun Tenant.toDetailDto(
        modules: List<TenantModuleStatusDto>,
        adminEmail: String?,
    ) = TenantDetailDto(
        id = id,
        code = code,
        name = name,
        legalName = legalName,
        countryCode = countryCode,
        timezone = timezone,
        defaultCurrency = defaultCurrency,
        locale = locale,
        dataRegion = dataRegion,
        isolationTier = isolationTier,
        status = status,
        subscriptionPlan = subscriptionPlan,
        adminEmail = adminEmail,
        modules = modules,
        createdAt = createdAt ?: Instant.now(),
        updatedAt = updatedAt ?: Instant.now(),
    )
}
