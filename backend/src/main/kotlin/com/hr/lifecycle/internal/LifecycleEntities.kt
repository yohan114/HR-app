package com.hr.lifecycle.internal

import com.hr.lifecycle.MovementStatus
import com.hr.lifecycle.MovementType
import com.hr.shared.persistence.TenantScopedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "career_movement")
class CareerMovement(
    @Column(name = "employee_id", nullable = false)
    var employeeId: UUID,
    @Column(name = "movement_number", nullable = false, length = 64)
    var movementNumber: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 32)
    var movementType: MovementType,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: MovementStatus = MovementStatus.DRAFT,
    @Column(name = "request_date", nullable = false)
    var requestDate: LocalDate,
    @Column(name = "effective_date", nullable = false)
    var effectiveDate: LocalDate,
    @Column(name = "initiator_id", nullable = false)
    var initiatorId: UUID,
    @Column(name = "approver_id")
    var approverId: UUID? = null,
    @Column(name = "approved_at")
    var approvedAt: Instant? = null,
    @Column(name = "reverted_at")
    var revertedAt: Instant? = null,
    @Column(name = "reversion_reason")
    var reversionReason: String? = null,
    @Column(name = "justification", nullable = false)
    var justification: String,
    @Column(name = "remarks")
    var remarks: String? = null,

    // Previous State Snapshot
    @Column(name = "prev_department_id")
    var prevDepartmentId: UUID? = null,
    @Column(name = "prev_department_name", length = 128)
    var prevDepartmentName: String? = null,
    @Column(name = "prev_designation_id")
    var prevDesignationId: UUID? = null,
    @Column(name = "prev_designation_name", length = 128)
    var prevDesignationName: String? = null,
    @Column(name = "prev_salary_grade_id")
    var prevSalaryGradeId: UUID? = null,
    @Column(name = "prev_salary_grade_code", length = 32)
    var prevSalaryGradeCode: String? = null,
    @Column(name = "prev_location_id")
    var prevLocationId: UUID? = null,
    @Column(name = "prev_location_name", length = 128)
    var prevLocationName: String? = null,
    @Column(name = "prev_supervisor_id")
    var prevSupervisorId: UUID? = null,
    @Column(name = "prev_supervisor_name", length = 128)
    var prevSupervisorName: String? = null,
    @Column(name = "prev_base_salary", precision = 14, scale = 2)
    var prevBaseSalary: BigDecimal? = null,
    @Column(name = "prev_currency", length = 3)
    var prevCurrency: String? = "LKR",

    // Proposed / New State
    @Column(name = "new_department_id")
    var newDepartmentId: UUID? = null,
    @Column(name = "new_department_name", length = 128)
    var newDepartmentName: String? = null,
    @Column(name = "new_designation_id")
    var newDesignationId: UUID? = null,
    @Column(name = "new_designation_name", length = 128)
    var newDesignationName: String? = null,
    @Column(name = "new_salary_grade_id")
    var newSalaryGradeId: UUID? = null,
    @Column(name = "new_salary_grade_code", length = 32)
    var newSalaryGradeCode: String? = null,
    @Column(name = "new_location_id")
    var newLocationId: UUID? = null,
    @Column(name = "new_location_name", length = 128)
    var newLocationName: String? = null,
    @Column(name = "new_supervisor_id")
    var newSupervisorId: UUID? = null,
    @Column(name = "new_supervisor_name", length = 128)
    var newSupervisorName: String? = null,
    @Column(name = "new_base_salary", precision = 14, scale = 2)
    var newBaseSalary: BigDecimal? = null,
    @Column(name = "new_currency", length = 3)
    var newCurrency: String? = "LKR",

    // Cascade Execution Audit
    @Column(name = "cascade_applied", nullable = false)
    var cascadeApplied: Boolean = false,
    @Column(name = "cascade_applied_at")
    var cascadeAppliedAt: Instant? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cascade_summary", columnDefinition = "jsonb", nullable = false)
    var cascadeSummary: MutableMap<String, Any?> = mutableMapOf(),
) : TenantScopedEntity()

@Entity
@Table(name = "employee_salary_history")
class EmployeeSalaryHistory(
    @Column(name = "employee_id", nullable = false)
    var employeeId: UUID,
    @Column(name = "career_movement_id")
    var careerMovementId: UUID? = null,
    @Column(name = "effective_from", nullable = false)
    var effectiveFrom: LocalDate,
    @Column(name = "effective_to")
    var effectiveTo: LocalDate? = null,
    @Column(name = "base_salary", nullable = false, precision = 14, scale = 2)
    var baseSalary: BigDecimal,
    @Column(name = "currency", nullable = false, length = 3)
    var currency: String = "LKR",
    @Column(name = "change_percentage", precision = 6, scale = 2)
    var changePercentage: BigDecimal? = null,
    @Column(name = "revision_reason", nullable = false, length = 128)
    var revisionReason: String,
    @Column(name = "is_current", nullable = false)
    var isCurrent: Boolean = true,
) : TenantScopedEntity()
