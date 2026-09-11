package com.hr.lifecycle

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Enterprise lifecycle career movement categories.
 */
enum class MovementType {
    PROMOTION,
    LATERAL_TRANSFER,
    DEMOTION,
    CONFIRMATION,
    SALARY_REVISION,
    DESIGNATION_CHANGE,
    SECONDMENT,
}

/**
 * State machine status of a career movement.
 */
enum class MovementStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    SCHEDULED,
    APPLIED,
    REJECTED,
    CANCELLED,
    REVERTED,
}

/**
 * Milestone classification for interactive employee career journey.
 */
enum class TimelineEventType {
    HIRE,
    CONFIRMATION,
    PROMOTION,
    LATERAL_TRANSFER,
    SALARY_REVISION,
    DESIGNATION_CHANGE,
    SECONDMENT,
    DEMOTION,
}

// ---------------------------------------------------------------------------
// DTOs matching OpenAPI 3.1 contract
// ---------------------------------------------------------------------------

data class CareerMovementItem(
    val id: UUID,
    val employeeId: UUID,
    val movementNumber: String,
    val movementType: MovementType,
    val status: MovementStatus,
    val requestDate: LocalDate,
    val effectiveDate: LocalDate,
    val initiatorId: UUID,
    val approverId: UUID? = null,
    val approvedAt: Instant? = null,
    val revertedAt: Instant? = null,
    val reversionReason: String? = null,
    val justification: String,
    val remarks: String? = null,
    val prevDepartmentId: UUID? = null,
    val prevDepartmentName: String? = null,
    val prevDesignationId: UUID? = null,
    val prevDesignationName: String? = null,
    val prevSalaryGradeId: UUID? = null,
    val prevSalaryGradeCode: String? = null,
    val prevLocationId: UUID? = null,
    val prevLocationName: String? = null,
    val prevSupervisorId: UUID? = null,
    val prevSupervisorName: String? = null,
    val prevBaseSalary: BigDecimal? = null,
    val prevCurrency: String? = "LKR",
    val newDepartmentId: UUID? = null,
    val newDepartmentName: String? = null,
    val newDesignationId: UUID? = null,
    val newDesignationName: String? = null,
    val newSalaryGradeId: UUID? = null,
    val newSalaryGradeCode: String? = null,
    val newLocationId: UUID? = null,
    val newLocationName: String? = null,
    val newSupervisorId: UUID? = null,
    val newSupervisorName: String? = null,
    val newBaseSalary: BigDecimal? = null,
    val newCurrency: String? = "LKR",
    val cascadeApplied: Boolean = false,
    val cascadeAppliedAt: Instant? = null,
    val cascadeSummary: Map<String, Any?>? = null,
    val createdAt: Instant,
)

data class CareerMovementListResponse(
    val totalCount: Int,
    val movements: List<CareerMovementItem>,
)

data class CareerTimelineEvent(
    val id: UUID,
    val movementId: UUID? = null,
    val eventType: TimelineEventType,
    val title: String,
    val date: LocalDate,
    val departmentName: String? = null,
    val designationName: String? = null,
    val salaryGradeCode: String? = null,
    val baseSalary: BigDecimal? = null,
    val currency: String? = null,
    val changePercentage: BigDecimal? = null,
    val description: String,
    val isMilestone: Boolean = true,
)

data class CareerTimelineResponse(
    val employeeId: UUID,
    val employeeName: String,
    val currentDesignation: String,
    val currentDepartment: String,
    val currentGrade: String,
    val joinDate: LocalDate,
    val confirmationDate: LocalDate? = null,
    val events: List<CareerTimelineEvent>,
)

data class CareerMovementProposalRequest(
    val employeeId: UUID,
    val movementType: MovementType,
    val effectiveDate: LocalDate,
    val justification: String,
    val remarks: String? = null,
    val newDepartmentId: UUID? = null,
    val newDesignationId: UUID? = null,
    val newSalaryGradeId: UUID? = null,
    val newLocationId: UUID? = null,
    val newSupervisorId: UUID? = null,
    val newBaseSalary: BigDecimal? = null,
    val newCurrency: String = "LKR",
)

data class CareerMovementRevertRequest(
    val reversionReason: String,
)

data class SalaryHistoryItem(
    val id: UUID,
    val employeeId: UUID,
    val careerMovementId: UUID? = null,
    val effectiveFrom: LocalDate,
    val effectiveTo: LocalDate? = null,
    val baseSalary: BigDecimal,
    val currency: String = "LKR",
    val changePercentage: BigDecimal? = null,
    val revisionReason: String,
    val isCurrent: Boolean,
)
