package com.hr.tenancy.internal

import com.hr.tenancy.IsolationTier
import com.hr.tenancy.TenantStatus
import java.time.Instant
import java.util.UUID

data class TenantModuleStatusDto(
    val moduleKey: String,
    val enabled: Boolean,
    val config: Map<String, Any?>? = emptyMap(),
    val updatedAt: Instant? = null,
)

data class TenantDetailDto(
    val id: UUID,
    val code: String,
    val name: String,
    val legalName: String? = null,
    val countryCode: String,
    val timezone: String,
    val defaultCurrency: String,
    val locale: String,
    val dataRegion: String,
    val isolationTier: IsolationTier,
    val status: TenantStatus,
    val subscriptionPlan: String? = null,
    val adminEmail: String? = null,
    val modules: List<TenantModuleStatusDto> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class TenantListResponseDto(
    val tenants: List<TenantDetailDto>,
)

data class CreateTenantPayloadDto(
    val code: String,
    val name: String,
    val legalName: String? = null,
    val countryCode: String,
    val timezone: String = "UTC",
    val defaultCurrency: String,
    val locale: String = "en",
    val dataRegion: String = "default",
    val isolationTier: IsolationTier = IsolationTier.SHARED,
    val subscriptionPlan: String? = "ENTERPRISE",
    val adminEmail: String? = null,
    val modules: Map<String, Boolean>? = null,
)

data class UpdateTenantPayloadDto(
    val name: String? = null,
    val legalName: String? = null,
    val timezone: String? = null,
    val defaultCurrency: String? = null,
    val locale: String? = null,
    val dataRegion: String? = null,
    val isolationTier: IsolationTier? = null,
    val subscriptionPlan: String? = null,
    val status: TenantStatus? = null,
    val adminEmail: String? = null,
)

data class UpdateTenantModulesPayloadDto(
    val modules: Map<String, Boolean>,
)

data class TenantModulesResponseDto(
    val modules: List<TenantModuleStatusDto>,
)
