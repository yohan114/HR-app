package com.hr.organisation.internal

import com.hr.shared.persistence.TenantScopedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.util.UUID

/**
 * Organisation master data.
 *
 * Relationships are held as raw ids rather than JPA associations. That is
 * deliberate: `@ManyToOne` here would make every employee read a candidate for
 * a lazy-loading cascade through department → company → parent company, and
 * the N+1 queries that produces are the single most common cause of a slow
 * page in a JPA application. The API layer resolves the handful of names it
 * needs in one batched query instead.
 */
@Entity
@Table(name = "company")
class Company(
    @Column(name = "code", nullable = false)
    var code: String,
    @Column(name = "name", nullable = false)
    var name: String,
    @Column(name = "country_code", nullable = false, length = 2)
    var countryCode: String,
    @Column(name = "currency", nullable = false, length = 3)
    var currency: String,
) : TenantScopedEntity() {
    @Column(name = "parent_id")
    var parentId: UUID? = null

    @Column(name = "legal_name")
    var legalName: String? = null

    @Column(name = "tax_registration", length = 64)
    var taxRegistration: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields", columnDefinition = "jsonb", nullable = false)
    var customFields: MutableMap<String, Any?> = mutableMapOf()
}

@Entity
@Table(name = "location")
class Location(
    @Column(name = "company_id", nullable = false)
    var companyId: UUID,
    @Column(name = "code", nullable = false)
    var code: String,
    @Column(name = "name", nullable = false)
    var name: String,
    @Column(name = "timezone", nullable = false, length = 64)
    var timezone: String,
) : TenantScopedEntity() {
    @Column(name = "parent_id")
    var parentId: UUID? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "address", columnDefinition = "jsonb", nullable = false)
    var address: MutableMap<String, Any?> = mutableMapOf()

    /**
     * Geofence centre. Null means this location cannot be geofenced, which is a
     * valid configuration — the attendance policy decides whether that matters.
     */
    @Column(name = "geo_lat", precision = 9, scale = 6)
    var geoLat: BigDecimal? = null

    @Column(name = "geo_lng", precision = 9, scale = 6)
    var geoLng: BigDecimal? = null

    @Column(name = "geofence_radius_m")
    var geofenceRadiusMetres: Int? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields", columnDefinition = "jsonb", nullable = false)
    var customFields: MutableMap<String, Any?> = mutableMapOf()

    val hasGeofence: Boolean
        get() = geoLat != null && geoLng != null && geofenceRadiusMetres != null
}

@Entity
@Table(name = "department")
class Department(
    @Column(name = "code", nullable = false)
    var code: String,
    @Column(name = "name", nullable = false)
    var name: String,
) : TenantScopedEntity() {
    @Column(name = "parent_id")
    var parentId: UUID? = null

    @Column(name = "company_id")
    var companyId: UUID? = null

    @Column(name = "cost_centre_id")
    var costCentreId: UUID? = null

    @Column(name = "head_employee_id")
    var headEmployeeId: UUID? = null

    @Column(name = "active", nullable = false)
    var active: Boolean = true

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields", columnDefinition = "jsonb", nullable = false)
    var customFields: MutableMap<String, Any?> = mutableMapOf()
}

@Entity
@Table(name = "designation")
class Designation(
    @Column(name = "code", nullable = false)
    var code: String,
    @Column(name = "name", nullable = false)
    var name: String,
) : TenantScopedEntity() {
    @Column(name = "salary_grade_id")
    var salaryGradeId: UUID? = null

    @Column(name = "corporate_title_id")
    var corporateTitleId: UUID? = null

    @Column(name = "active", nullable = false)
    var active: Boolean = true

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields", columnDefinition = "jsonb", nullable = false)
    var customFields: MutableMap<String, Any?> = mutableMapOf()
}

@Entity
@Table(name = "cost_centre")
class CostCentre(
    @Column(name = "code", nullable = false)
    var code: String,
    @Column(name = "name", nullable = false)
    var name: String,
) : TenantScopedEntity() {
    @Column(name = "parent_id")
    var parentId: UUID? = null

    @Column(name = "company_id")
    var companyId: UUID? = null

    @Column(name = "gl_code", length = 64)
    var glCode: String? = null

    @Column(name = "active", nullable = false)
    var active: Boolean = true
}

/**
 * A salary band.
 *
 * Amounts are `BigDecimal`, never `Double`. Decided once, here, and depended on
 * by the payroll engine — see docs/03-architecture.md §7.
 */
@Entity
@Table(name = "salary_grade")
class SalaryGrade(
    @Column(name = "code", nullable = false)
    var code: String,
    @Column(name = "name", nullable = false)
    var name: String,
) : TenantScopedEntity() {
    @Column(name = "min_amount", precision = 19, scale = 6)
    var minAmount: BigDecimal? = null

    @Column(name = "mid_amount", precision = 19, scale = 6)
    var midAmount: BigDecimal? = null

    @Column(name = "max_amount", precision = 19, scale = 6)
    var maxAmount: BigDecimal? = null

    @Column(name = "currency", length = 3)
    var currency: String? = null

    @Column(name = "sequence", nullable = false)
    var sequence: Int = 0

    @Column(name = "active", nullable = false)
    var active: Boolean = true

    /** Whether an amount sits inside the band. Used when validating a salary amendment. */
    fun contains(amount: BigDecimal): Boolean =
        (minAmount == null || amount >= minAmount) && (maxAmount == null || amount <= maxAmount)
}
