package com.hr.lifecycle.internal

import com.hr.employee.EmployeeLookupService
import com.hr.lifecycle.*
import com.hr.organisation.OrganisationLookupService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Service
class LifecycleService(
    private val careerMovementRepository: CareerMovementRepository,
    private val salaryHistoryRepository: EmployeeSalaryHistoryRepository,
    private val employeeLookupService: EmployeeLookupService,
    private val organisationLookupService: OrganisationLookupService,
    private val cascadeEngine: CareerCascadeEngine,
) {

    private val defaultTenantId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    /**
     * Lists employee career movements with optional filtering.
     */
    @Transactional(readOnly = true)
    fun getCareerMovements(
        employeeId: UUID? = null,
        status: MovementStatus? = null,
        type: MovementType? = null,
        tenantId: UUID = defaultTenantId,
    ): CareerMovementListResponse {
        val all = if (employeeId != null) {
            careerMovementRepository.findAllByTenantIdAndEmployeeIdOrderByEffectiveDateDesc(tenantId, employeeId)
        } else {
            careerMovementRepository.findAllByTenantIdOrderByEffectiveDateDesc(tenantId)
        }

        val filtered = all.filter { mov ->
            (status == null || mov.status == status) &&
            (type == null || mov.movementType == type)
        }

        return CareerMovementListResponse(
            totalCount = filtered.size,
            movements = filtered.map { it.toItem() },
        )
    }

    /**
     * Generates an interactive career timeline for an employee.
     */
    @Transactional(readOnly = true)
    fun getCareerTimeline(
        employeeId: UUID? = null,
        tenantId: UUID = defaultTenantId,
    ): CareerTimelineResponse {
        val targetId = employeeId ?: defaultTenantId // Fallback to default employee
        val employee = employeeLookupService.findById(targetId)
            ?: error("Employee record not found: $targetId")

        val departmentName = employee.departmentId?.let { organisationLookupService.findDepartmentName(it) } ?: "Engineering"
        val designationName = employee.designationId?.let { organisationLookupService.findDesignationName(it) } ?: "Software Engineer"
        val gradeCode = employee.salaryGradeId?.let { organisationLookupService.findSalaryGradeCode(it) } ?: "E2"

        val movements = careerMovementRepository.findAllByTenantIdAndEmployeeIdOrderByEffectiveDateDesc(tenantId, targetId)
            .sortedBy { it.effectiveDate }
        val salaryHistories = salaryHistoryRepository.findAllByTenantIdAndEmployeeIdOrderByEffectiveFromDesc(tenantId, targetId)
            .associateBy { it.careerMovementId }

        val events = mutableListOf<CareerTimelineEvent>()

        // 1. Initial Hire Milestone
        val initialSalary = salaryHistoryRepository.findAllByTenantIdAndEmployeeIdOrderByEffectiveFromDesc(tenantId, targetId)
            .find { it.careerMovementId == null }

        events.add(
            CareerTimelineEvent(
                id = UUID.nameUUIDFromBytes("hire-${employee.id}".toByteArray()),
                movementId = null,
                eventType = TimelineEventType.HIRE,
                title = "Joined Company",
                date = employee.joinDate,
                departmentName = departmentName,
                designationName = "Associate Software Engineer",
                salaryGradeCode = "E1",
                baseSalary = initialSalary?.baseSalary,
                currency = initialSalary?.currency ?: "LKR",
                changePercentage = null,
                description = "Started full-time career journey at Acme Corp",
                isMilestone = true,
            )
        )

        // 2. Subsequent Lifecycle Events
        movements.forEach { mov ->
            val eventType = when (mov.movementType) {
                MovementType.CONFIRMATION -> TimelineEventType.CONFIRMATION
                MovementType.PROMOTION -> TimelineEventType.PROMOTION
                MovementType.LATERAL_TRANSFER -> TimelineEventType.LATERAL_TRANSFER
                MovementType.SALARY_REVISION -> TimelineEventType.SALARY_REVISION
                MovementType.DESIGNATION_CHANGE -> TimelineEventType.DESIGNATION_CHANGE
                MovementType.SECONDMENT -> TimelineEventType.SECONDMENT
                MovementType.DEMOTION -> TimelineEventType.DEMOTION
            }

            val sal = salaryHistories[mov.id]
            val title = when (mov.movementType) {
                MovementType.CONFIRMATION -> "Probation Confirmed"
                MovementType.PROMOTION -> "Promoted to ${mov.newDesignationName ?: "Senior Cadre"}"
                MovementType.LATERAL_TRANSFER -> "Transferred to ${mov.newDepartmentName ?: "New Team"}"
                MovementType.SALARY_REVISION -> "Annual Salary Revision"
                MovementType.DESIGNATION_CHANGE -> "Role Reclassification"
                MovementType.SECONDMENT -> "Special Secondment"
                MovementType.DEMOTION -> "Role Reassignment"
            }

            events.add(
                CareerTimelineEvent(
                    id = mov.id,
                    movementId = mov.id,
                    eventType = eventType,
                    title = title,
                    date = mov.effectiveDate,
                    departmentName = mov.newDepartmentName ?: mov.prevDepartmentName,
                    designationName = mov.newDesignationName ?: mov.prevDesignationName,
                    salaryGradeCode = mov.newSalaryGradeCode ?: mov.prevSalaryGradeCode,
                    baseSalary = sal?.baseSalary ?: mov.newBaseSalary,
                    currency = sal?.currency ?: mov.newCurrency ?: "LKR",
                    changePercentage = sal?.changePercentage,
                    description = mov.justification,
                    isMilestone = (mov.movementType == MovementType.PROMOTION || mov.movementType == MovementType.CONFIRMATION),
                )
            )
        }

        return CareerTimelineResponse(
            employeeId = employee.id ?: targetId,
            employeeName = "${employee.firstName} ${employee.lastName}",
            currentDesignation = designationName,
            currentDepartment = departmentName,
            currentGrade = gradeCode,
            joinDate = employee.joinDate,
            confirmationDate = employee.confirmationDate,
            events = events.sortedByDescending { it.date },
        )
    }

    /**
     * Retrieves career movement by ID.
     */
    @Transactional(readOnly = true)
    fun getCareerMovementById(id: UUID, tenantId: UUID = defaultTenantId): CareerMovementItem {
        val movement = careerMovementRepository.findByIdAndTenantId(id, tenantId)
            ?: error("Career movement not found: $id")
        return movement.toItem()
    }

    /**
     * Proposes a new career movement with automatic snapshotting.
     */
    @Transactional
    fun proposeCareerMovement(
        request: CareerMovementProposalRequest,
        initiatorId: UUID,
        tenantId: UUID = defaultTenantId,
    ): CareerMovementItem {
        val employee = employeeLookupService.findById(request.employeeId)
            ?: error("Target employee not found: ${request.employeeId}")

        val currentSalary = salaryHistoryRepository.findByTenantIdAndEmployeeIdAndIsCurrentTrue(tenantId, request.employeeId)
        val prevDeptName = employee.departmentId?.let { organisationLookupService.findDepartmentName(it) }
        val prevDesigName = employee.designationId?.let { organisationLookupService.findDesignationName(it) }
        val prevGradeCode = employee.salaryGradeId?.let { organisationLookupService.findSalaryGradeCode(it) }
        val prevLocName = employee.locationId?.let { organisationLookupService.findLocationName(it) }
        val prevSuperName = employee.supervisorId?.let { employeeLookupService.findDisplayName(it) }

        val newDeptName = request.newDepartmentId?.let { organisationLookupService.findDepartmentName(it) } ?: prevDeptName
        val newDesigName = request.newDesignationId?.let { organisationLookupService.findDesignationName(it) } ?: prevDesigName
        val newGradeCode = request.newSalaryGradeId?.let { organisationLookupService.findSalaryGradeCode(it) } ?: prevGradeCode
        val newLocName = request.newLocationId?.let { organisationLookupService.findLocationName(it) } ?: prevLocName
        val newSuperName = request.newSupervisorId?.let { employeeLookupService.findDisplayName(it) } ?: prevSuperName

        val movementCount = careerMovementRepository.countByTenantId(tenantId) + 1
        val movementNumber = "MOV-${LocalDate.now().year}-${String.format("%04d", movementCount)}"

        val movement = CareerMovement(
            employeeId = request.employeeId,
            movementNumber = movementNumber,
            movementType = request.movementType,
            status = MovementStatus.SUBMITTED,
            requestDate = LocalDate.now(),
            effectiveDate = request.effectiveDate,
            initiatorId = initiatorId,
            justification = request.justification,
            remarks = request.remarks,
            prevDepartmentId = employee.departmentId,
            prevDepartmentName = prevDeptName,
            prevDesignationId = employee.designationId,
            prevDesignationName = prevDesigName,
            prevSalaryGradeId = employee.salaryGradeId,
            prevSalaryGradeCode = prevGradeCode,
            prevLocationId = employee.locationId,
            prevLocationName = prevLocName,
            prevSupervisorId = employee.supervisorId,
            prevSupervisorName = prevSuperName,
            prevBaseSalary = currentSalary?.baseSalary,
            prevCurrency = currentSalary?.currency ?: "LKR",
            newDepartmentId = request.newDepartmentId ?: employee.departmentId,
            newDepartmentName = newDeptName,
            newDesignationId = request.newDesignationId ?: employee.designationId,
            newDesignationName = newDesigName,
            newSalaryGradeId = request.newSalaryGradeId ?: employee.salaryGradeId,
            newSalaryGradeCode = newGradeCode,
            newLocationId = request.newLocationId ?: employee.locationId,
            newLocationName = newLocName,
            newSupervisorId = request.newSupervisorId ?: employee.supervisorId,
            newSupervisorName = newSuperName,
            newBaseSalary = request.newBaseSalary ?: currentSalary?.baseSalary,
            newCurrency = request.newCurrency,
        ).apply {
            this.tenantId = tenantId
        }

        val saved = careerMovementRepository.save(movement)
        return saved.toItem()
    }

    /**
     * Approves career movement and executes cascade if effective immediately.
     */
    @Transactional
    fun approveCareerMovement(
        id: UUID,
        approverId: UUID,
        tenantId: UUID = defaultTenantId,
    ): CareerMovementItem {
        val movement = careerMovementRepository.findByIdAndTenantId(id, tenantId)
            ?: error("Career movement not found: $id")

        if (movement.status != MovementStatus.SUBMITTED && movement.status != MovementStatus.DRAFT) {
            error("Cannot approve career movement in status ${movement.status}")
        }

        movement.approverId = approverId
        movement.approvedAt = java.time.Instant.now()

        val today = LocalDate.now()
        val processed = if (!movement.effectiveDate.isAfter(today)) {
            cascadeEngine.applyCascade(movement)
        } else {
            movement.status = MovementStatus.SCHEDULED
            movement.cascadeSummary["scheduledFor"] = movement.effectiveDate.toString()
            careerMovementRepository.save(movement)
        }

        return processed.toItem()
    }

    /**
     * Reverts career movement and restores prior employee master state.
     */
    @Transactional
    fun revertCareerMovement(
        id: UUID,
        reason: String,
        tenantId: UUID = defaultTenantId,
    ): CareerMovementItem {
        val movement = careerMovementRepository.findByIdAndTenantId(id, tenantId)
            ?: error("Career movement not found: $id")

        if (movement.status == MovementStatus.REVERTED || movement.status == MovementStatus.CANCELLED) {
            error("Movement $id is already in status ${movement.status}")
        }

        val reverted = cascadeEngine.revertCascade(movement, reason)
        return reverted.toItem()
    }

    private fun CareerMovement.toItem(): CareerMovementItem = CareerMovementItem(
        id = this.id ?: UUID.randomUUID(),
        employeeId = this.employeeId,
        movementNumber = this.movementNumber,
        movementType = this.movementType,
        status = this.status,
        requestDate = this.requestDate,
        effectiveDate = this.effectiveDate,
        initiatorId = this.initiatorId,
        approverId = this.approverId,
        approvedAt = this.approvedAt,
        revertedAt = this.revertedAt,
        reversionReason = this.reversionReason,
        justification = this.justification,
        remarks = this.remarks,
        prevDepartmentId = this.prevDepartmentId,
        prevDepartmentName = this.prevDepartmentName,
        prevDesignationId = this.prevDesignationId,
        prevDesignationName = this.prevDesignationName,
        prevSalaryGradeId = this.prevSalaryGradeId,
        prevSalaryGradeCode = this.prevSalaryGradeCode,
        prevLocationId = this.prevLocationId,
        prevLocationName = this.prevLocationName,
        prevSupervisorId = this.prevSupervisorId,
        prevSupervisorName = this.prevSupervisorName,
        prevBaseSalary = this.prevBaseSalary,
        prevCurrency = this.prevCurrency,
        newDepartmentId = this.newDepartmentId,
        newDepartmentName = this.newDepartmentName,
        newDesignationId = this.newDesignationId,
        newDesignationName = this.newDesignationName,
        newSalaryGradeId = this.newSalaryGradeId,
        newSalaryGradeCode = this.newSalaryGradeCode,
        newLocationId = this.newLocationId,
        newLocationName = this.newLocationName,
        newSupervisorId = this.newSupervisorId,
        newSupervisorName = this.newSupervisorName,
        newBaseSalary = this.newBaseSalary,
        newCurrency = this.newCurrency,
        cascadeApplied = this.cascadeApplied,
        cascadeAppliedAt = this.cascadeAppliedAt,
        cascadeSummary = this.cascadeSummary,
        createdAt = this.createdAt ?: java.time.Instant.now(),
    )
}
