package com.hr.tenancy.internal

import com.hr.shared.persistence.BaseEntity
import com.hr.tenancy.IsolationTier
import com.hr.tenancy.TenantHandle
import com.hr.tenancy.TenantStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

/**
 * A customer organisation.
 *
 * This is the one entity that is deliberately **not** tenant-scoped — it is the registry that
 * defines tenants, so it cannot itself be filtered by tenant. It is protected instead by
 * requiring the platform database role, and by never exposing it on a tenant-facing endpoint.
 */
@Entity
@Table(name = "tenant")
class Tenant(
    @Column(name = "code", nullable = false, unique = true)
    var code: String,
    @Column(name = "name", nullable = false)
    var name: String,
    @Column(name = "legal_name")
    var legalName: String? = null,
    @Column(name = "country_code", nullable = false, length = 2)
    var countryCode: String,
    @Column(name = "timezone", nullable = false)
    var timezone: String = "UTC",
    @Column(name = "default_currency", nullable = false, length = 3)
    var defaultCurrency: String,
    @Column(name = "locale", nullable = false, length = 16)
    var locale: String = "en",
    /**
     * Where this tenant's data physically lives. Drives connection routing for customers under
     * data-residency obligations (UAE and Indonesia both have them).
     */
    @Column(name = "data_region", nullable = false, length = 32)
    var dataRegion: String = "default",
    @Enumerated(EnumType.STRING)
    @Column(name = "isolation_tier", nullable = false, length = 32)
    var isolationTier: IsolationTier = IsolationTier.SHARED,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: TenantStatus = TenantStatus.PROVISIONING,
    @Column(name = "subscription_plan", length = 64)
    var subscriptionPlan: String? = null,
) : BaseEntity() {
    fun toHandle() =
        TenantHandle(
            id = id,
            code = code,
            name = name,
            dataRegion = dataRegion,
            defaultCurrency = defaultCurrency,
            timezone = timezone,
            locale = locale,
            isolationTier = isolationTier,
            status = status,
        )
}
