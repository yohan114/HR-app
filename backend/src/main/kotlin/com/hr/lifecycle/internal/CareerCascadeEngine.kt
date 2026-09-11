package com.hr.lifecycle.internal

import com.hr.employee.EmployeeLookupService
import com.hr.lifecycle.MovementStatus
import com.hr.lifecycle.MovementType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

/**
 * Transactional cascade applier and rollback engine for employee lifecycle movements.
 *
 * Automatically updates:
 * 1. Employee master record (designation, department, branch/location, supervisor, confirmation date).
 * 2. Salary progression ledger (effective-dated compensation history, percentage increase).
 * 3. Auditable snapshot diffs with complete reversible rollback capabilities.
 */
@Component
class CareerCascadeEngine(
    private val employeeLookupService: EmployeeLookupService,
    private val salaryHistoryRepository: EmployeeSalaryHistoryRepository,
    private val careerMovementRepository: CareerMovementRepository,
) {

    @Transactional
    fun applyCascade(movement: CareerMovement): CareerMovement {
        if (movement.cascadeApplied) {
            return movement
        }

        // 1. Employee Master updates
        employeeLookupService.applyCareerCascade(
            employeeId = movement.employeeId,
            departmentId = movement.newDepartmentId,
            designationId = movement.newDesignationId,
            salaryGradeId = movement.newSalaryGradeId,
            locationId = movement.newLocationId,
            supervisorId = movement.newSupervisorId,
            confirmationDate = if (movement.movementType == MovementType.CONFIRMATION) movement.effectiveDate else null,
        )

        // 2. Compensation History update
        val tenantId = movement.tenantId ?: UUID.fromString("00000000-0000-0000-0000-000000000001")
        if (movement.newBaseSalary != null && movement.newBaseSalary!! > BigDecimal.ZERO) {
            val currentSalary = salaryHistoryRepository.findByTenantIdAndEmployeeIdAndIsCurrentTrue(
                tenantId = tenantId,
                employeeId = movement.employeeId,
            )

            currentSalary?.let {
                it.isCurrent = false
                it.effectiveTo = movement.effectiveDate.minusDays(1)
                salaryHistoryRepository.save(it)
            }

            val changePercentage = if (movement.prevBaseSalary != null && movement.prevBaseSalary!! > BigDecimal.ZERO) {
                movement.newBaseSalary!!.minus(movement.prevBaseSalary!!)
                    .divide(movement.prevBaseSalary!!, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal("100.00"))
                    .setScale(2, RoundingMode.HALF_UP)
            } else null

            val newSalary = EmployeeSalaryHistory(
                employeeId = movement.employeeId,
                careerMovementId = movement.id,
                effectiveFrom = movement.effectiveDate,
                effectiveTo = null,
                baseSalary = movement.newBaseSalary!!,
                currency = movement.newCurrency ?: "LKR",
                changePercentage = changePercentage,
                revisionReason = "${movement.movementType.name.replace("_", " ")} compensation revision",
                isCurrent = true,
            ).apply {
                this.tenantId = tenantId
            }

            salaryHistoryRepository.save(newSalary)
        }

        // 3. Movement status & cascade audit
        movement.cascadeApplied = true
        movement.cascadeAppliedAt = Instant.now()
        movement.status = MovementStatus.APPLIED
        movement.cascadeSummary = mutableMapOf(
            "appliedAt" to Instant.now().toString(),
            "departmentUpdated" to (movement.newDepartmentId != null),
            "designationUpdated" to (movement.newDesignationId != null),
            "gradeUpdated" to (movement.newSalaryGradeId != null),
            "salaryRevised" to (movement.newBaseSalary != null),
            "confirmationDateSet" to (movement.movementType == MovementType.CONFIRMATION),
        )

        return careerMovementRepository.save(movement)
    }

    @Transactional
    fun revertCascade(movement: CareerMovement, reason: String): CareerMovement {
        val tenantId = movement.tenantId ?: UUID.fromString("00000000-0000-0000-0000-000000000001")

        if (movement.cascadeApplied) {
            // Revert employee master to prior state snapshot
            employeeLookupService.revertCareerCascade(
                employeeId = movement.employeeId,
                prevDepartmentId = movement.prevDepartmentId,
                prevDesignationId = movement.prevDesignationId,
                prevSalaryGradeId = movement.prevSalaryGradeId,
                prevLocationId = movement.prevLocationId,
                prevSupervisorId = movement.prevSupervisorId,
                clearConfirmationDate = (movement.movementType == MovementType.CONFIRMATION),
            )

            // Revert salary progression
            val appliedSalary = salaryHistoryRepository.findByTenantIdAndCareerMovementId(tenantId, movement.id)
            if (appliedSalary != null) {
                salaryHistoryRepository.delete(appliedSalary)
                val remaining = salaryHistoryRepository.findAllByTenantIdAndEmployeeIdOrderByEffectiveFromDesc(
                    tenantId = tenantId,
                    employeeId = movement.employeeId,
                )
                remaining.firstOrNull()?.let {
                    it.isCurrent = true
                    it.effectiveTo = null
                    salaryHistoryRepository.save(it)
                }
            }
        }

        movement.cascadeApplied = false
        movement.status = MovementStatus.REVERTED
        movement.revertedAt = Instant.now()
        movement.reversionReason = reason
        movement.cascadeSummary["revertedAt"] = Instant.now().toString()
        movement.cascadeSummary["reversionReason"] = reason

        return careerMovementRepository.save(movement)
    }
}
