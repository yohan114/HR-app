package com.hr.timesheet.internal

import com.hr.shared.persistence.TenantScopedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "timesheet_client")
class TimesheetClientEntity(
    @Column(name = "client_code", nullable = false, length = 50)
    var clientCode: String,

    @Column(name = "name", nullable = false, length = 150)
    var clientName: String,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "currency", nullable = false, length = 10)
    var billingCurrency: String = "USD",

    @Column(name = "contact_email", length = 100)
    var contactEmail: String? = null,

    @Column(name = "contact_person", length = 100)
    var contactPerson: String? = null,
) : TenantScopedEntity() {
    @get:Transient
    val status: String get() = if (isActive) "ACTIVE" else "INACTIVE"
}

@Entity
@Table(name = "timesheet_project")
class TimesheetProjectEntity(
    @Column(name = "client_id", nullable = false)
    var clientId: UUID,

    @Column(name = "project_code", nullable = false, length = 50)
    var projectCode: String,

    @Column(name = "name", nullable = false, length = 150)
    var projectName: String,

    @Column(name = "description", columnDefinition = "text")
    var description: String? = null,

    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate = LocalDate.now(),

    @Column(name = "end_date")
    var endDate: LocalDate? = null,

    @Column(name = "budget_amount", precision = 12, scale = 2)
    var budgetAmount: BigDecimal? = null,

    @Column(name = "budget_hours", precision = 8, scale = 2)
    var budgetHours: BigDecimal? = null,

    @Column(name = "is_billable", nullable = false)
    var billable: Boolean = true,

    @Column(name = "status", nullable = false, length = 30)
    var status: String = "ACTIVE",
) : TenantScopedEntity()

@Entity
@Table(name = "timesheet_activity")
class TimesheetActivityEntity(
    @Column(name = "project_id", nullable = false)
    var projectId: UUID,

    @Column(name = "activity_code", nullable = false, length = 50)
    var activityCode: String,

    @Column(name = "name", nullable = false, length = 150)
    var activityName: String,

    @Column(name = "description", columnDefinition = "text")
    var description: String? = null,

    @Column(name = "is_billable", nullable = false)
    var billable: Boolean = true,

    @Column(name = "default_rate", precision = 10, scale = 2)
    var defaultRate: BigDecimal = BigDecimal.ZERO,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
) : TenantScopedEntity() {
    @get:Transient
    val status: String get() = if (isActive) "ACTIVE" else "INACTIVE"
}

@Entity
@Table(name = "timesheet")
class TimesheetEntity(
    @Column(name = "employee_id", nullable = false)
    var employeeId: UUID,

    @Column(name = "period_start", nullable = false)
    var weekStartDate: LocalDate,

    @Column(name = "period_end", nullable = false)
    var weekEndDate: LocalDate,

    @Column(name = "status", nullable = false, length = 30)
    var status: String = "DRAFT",

    @Column(name = "total_hours", nullable = false, precision = 6, scale = 2)
    var totalHours: BigDecimal = BigDecimal.ZERO,

    @Column(name = "billable_hours", nullable = false, precision = 6, scale = 2)
    var billableHours: BigDecimal = BigDecimal.ZERO,

    @Column(name = "submitted_at")
    var submittedAt: Instant? = null,

    @Column(name = "approved_at")
    var approvedAt: Instant? = null,

    @Column(name = "approved_by")
    var approverId: UUID? = null,

    @Column(name = "rejection_reason", columnDefinition = "text")
    var rejectionReason: String? = null,

    @Column(name = "comments", columnDefinition = "text")
    var comments: String? = null,
) : TenantScopedEntity() {
    @get:Transient
    val nonBillableHours: BigDecimal
        get() = totalHours.subtract(billableHours)
}

@Entity
@Table(name = "timesheet_entry")
class TimesheetEntryEntity(
    @Column(name = "timesheet_id", nullable = false)
    var timesheetId: UUID,

    @Column(name = "project_id", nullable = false)
    var projectId: UUID,

    @Column(name = "activity_id", nullable = false)
    var activityId: UUID,

    @Column(name = "work_date", nullable = false)
    var entryDate: LocalDate,

    @Column(name = "hours", nullable = false, precision = 5, scale = 2)
    var hours: BigDecimal = BigDecimal.ZERO,

    @Column(name = "is_billable", nullable = false)
    var billable: Boolean = true,

    @Column(name = "rate", precision = 10, scale = 2)
    var rate: BigDecimal = BigDecimal.ZERO,

    @Column(name = "amount", precision = 12, scale = 2)
    var amount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "description", columnDefinition = "text")
    var notes: String? = null,
) : TenantScopedEntity()
