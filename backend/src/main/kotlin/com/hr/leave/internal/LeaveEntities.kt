package com.hr.leave.internal

import com.hr.leave.AccrualMethod
import com.hr.leave.LeaveYearStatus
import com.hr.leave.LedgerEntryType
import com.hr.shared.persistence.TenantScopedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "leave_year")
class LeaveYear(
    @Column(name = "code", nullable = false, length = 32)
    var code: String,
    @Column(name = "name", nullable = false, length = 128)
    var name: String,
    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,
    @Column(name = "end_date", nullable = false)
    var endDate: LocalDate,
) : TenantScopedEntity() {
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: LeaveYearStatus = LeaveYearStatus.ACTIVE
}

@Entity
@Table(name = "leave_type")
class LeaveType(
    @Column(name = "code", nullable = false, length = 32)
    var code: String,
    @Column(name = "name", nullable = false, length = 128)
    var name: String,
) : TenantScopedEntity() {
    @Column(name = "unit", nullable = false, length = 16)
    var unit: String = "DAY"

    @Column(name = "paid", nullable = false)
    var paid: Boolean = true

    @Column(name = "allow_half_day", nullable = false)
    var allowHalfDay: Boolean = true

    @Column(name = "color", nullable = false, length = 32)
    var color: String = "#6366f1"

    @Column(name = "sequence", nullable = false)
    var sequence: Short = 0
}

@Entity
@Table(name = "leave_entitlement_rule")
class LeaveEntitlementRule(
    @Column(name = "leave_type_id", nullable = false)
    var leaveTypeId: UUID,
    @Column(name = "name", nullable = false, length = 128)
    var name: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "accrual_method", nullable = false, length = 32)
    var accrualMethod: AccrualMethod,
) : TenantScopedEntity() {
    @Column(name = "accrual_rate", nullable = false, precision = 8, scale = 4)
    var accrualRate: BigDecimal = BigDecimal.ZERO

    @Column(name = "accrual_frequency", nullable = false, length = 32)
    var accrualFrequency: String = "ANNUAL"

    @Column(name = "prorate_on_join", nullable = false)
    var prorateOnJoin: Boolean = true

    @Column(name = "prorate_on_exit", nullable = false)
    var prorateOnExit: Boolean = true

    @Column(name = "carry_forward_enabled", nullable = false)
    var carryForwardEnabled: Boolean = false

    @Column(name = "carry_forward_max", nullable = false, precision = 6, scale = 2)
    var carryForwardMax: BigDecimal = BigDecimal.ZERO

    @Column(name = "carry_forward_expiry_months", nullable = false)
    var carryForwardExpiryMonths: Int = 0

    @Column(name = "encashment_enabled", nullable = false)
    var encashmentEnabled: Boolean = false

    @Column(name = "encashment_max", nullable = false, precision = 6, scale = 2)
    var encashmentMax: BigDecimal = BigDecimal.ZERO

    @Column(name = "max_balance", nullable = false, precision = 6, scale = 2)
    var maxBalance: BigDecimal = BigDecimal("999.00")

    @Column(name = "service_based_slabs", nullable = false, columnDefinition = "jsonb")
    var serviceBasedSlabs: String = "[]"

    @Column(name = "effective_from", nullable = false)
    var effectiveFrom: LocalDate = LocalDate.now()
}

/**
 * Composite primary key for [EmployeeLeaveEntitlement].
 */
data class EmployeeLeaveEntitlementId(
    var tenantId: UUID? = null,
    var employeeId: UUID? = null,
    var leaveYearId: UUID? = null,
    var leaveTypeId: UUID? = null,
) : Serializable

@Entity
@Table(name = "employee_leave_entitlement")
@IdClass(EmployeeLeaveEntitlementId::class)
class EmployeeLeaveEntitlement(
    @Id
    @Column(name = "tenant_id", nullable = false)
    var tenantId: UUID,
    @Id
    @Column(name = "employee_id", nullable = false)
    var employeeId: UUID,
    @Id
    @Column(name = "leave_year_id", nullable = false)
    var leaveYearId: UUID,
    @Id
    @Column(name = "leave_type_id", nullable = false)
    var leaveTypeId: UUID,
) {
    @Column(name = "opening_balance", nullable = false, precision = 6, scale = 2)
    var openingBalance: BigDecimal = BigDecimal.ZERO

    @Column(name = "accrued", nullable = false, precision = 6, scale = 2)
    var accrued: BigDecimal = BigDecimal.ZERO

    @Column(name = "taken", nullable = false, precision = 6, scale = 2)
    var taken: BigDecimal = BigDecimal.ZERO

    @Column(name = "adjusted", nullable = false, precision = 6, scale = 2)
    var adjusted: BigDecimal = BigDecimal.ZERO

    @Column(name = "carried_forward", nullable = false, precision = 6, scale = 2)
    var carriedForward: BigDecimal = BigDecimal.ZERO

    @Column(name = "encashed", nullable = false, precision = 6, scale = 2)
    var encashed: BigDecimal = BigDecimal.ZERO

    @Column(name = "expired", nullable = false, precision = 6, scale = 2)
    var expired: BigDecimal = BigDecimal.ZERO

    @Column(name = "balance", nullable = false, precision = 6, scale = 2)
    var balance: BigDecimal = BigDecimal.ZERO

    @Column(name = "last_accrued_at")
    var lastAccruedAt: Instant? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
}

@Entity
@Table(name = "leave_ledger")
class LeaveLedgerEntry(
    @Column(name = "employee_id", nullable = false)
    var employeeId: UUID,
    @Column(name = "leave_year_id", nullable = false)
    var leaveYearId: UUID,
    @Column(name = "leave_type_id", nullable = false)
    var leaveTypeId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 32)
    var entryType: LedgerEntryType,
    @Column(name = "days", nullable = false, precision = 6, scale = 2)
    var days: BigDecimal,
    @Column(name = "reference_type", nullable = false, length = 64)
    var referenceType: String,
    @Column(name = "reference_id", nullable = false, length = 128)
    var referenceId: String,
    @Column(name = "effective_date", nullable = false)
    var effectiveDate: LocalDate,
    @Column(name = "balance_after", nullable = false, precision = 6, scale = 2)
    var balanceAfter: BigDecimal,
    @Column(name = "remarks", length = 255)
    var remarks: String? = null,
) : TenantScopedEntity()

@Entity
@Table(name = "public_holiday")
class PublicHoliday(
    @Column(name = "holiday_date", nullable = false)
    var holidayDate: LocalDate,
    @Column(name = "name", nullable = false, length = 128)
    var name: String,
    @Column(name = "country_code", nullable = false, length = 2)
    var countryCode: String = "LK",
    @Column(name = "is_half_day", nullable = false)
    var isHalfDay: Boolean = false,
) : TenantScopedEntity()

@Entity
@Table(name = "leave_application")
class LeaveApplication(
    @Column(name = "employee_id", nullable = false)
    var employeeId: UUID,
    @Column(name = "leave_year_id", nullable = false)
    var leaveYearId: UUID,
    @Column(name = "leave_type_id", nullable = false)
    var leaveTypeId: UUID,
    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,
    @Column(name = "end_date", nullable = false)
    var endDate: LocalDate,
    @Column(name = "total_days", nullable = false, precision = 5, scale = 2)
    var totalDays: BigDecimal,
    @Enumerated(EnumType.STRING)
    @Column(name = "day_portion", nullable = false, length = 16)
    var dayPortion: com.hr.leave.DayPortion = com.hr.leave.DayPortion.FULL_DAY,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: com.hr.leave.ApplicationStatus = com.hr.leave.ApplicationStatus.SUBMITTED,
    @Column(name = "reason")
    var reason: String? = null,
    @Column(name = "submitted_at", nullable = false)
    var submittedAt: Instant = Instant.now(),
    @Column(name = "actioned_at")
    var actionedAt: Instant? = null,
    @Column(name = "actioned_by")
    var actionedBy: UUID? = null,
    @Column(name = "action_reason")
    var actionReason: String? = null,
) : TenantScopedEntity()

@Entity
@Table(name = "leave_application_day")
class LeaveApplicationDay(
    @Column(name = "leave_application_id", nullable = false)
    var leaveApplicationId: UUID,
    @Column(name = "day_date", nullable = false)
    var dayDate: LocalDate,
    @Enumerated(EnumType.STRING)
    @Column(name = "day_portion", nullable = false, length = 16)
    var dayPortion: com.hr.leave.DayPortion = com.hr.leave.DayPortion.FULL_DAY,
    @Column(name = "is_working_day", nullable = false)
    var isWorkingDay: Boolean = true,
    @Column(name = "hours", nullable = false, precision = 4, scale = 2)
    var hours: BigDecimal = BigDecimal("8.00"),
) : TenantScopedEntity()

