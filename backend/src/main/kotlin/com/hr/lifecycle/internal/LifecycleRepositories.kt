package com.hr.lifecycle.internal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface CareerMovementRepository : JpaRepository<CareerMovement, UUID> {
    fun findAllByTenantIdAndEmployeeIdOrderByEffectiveDateDesc(tenantId: UUID, employeeId: UUID): List<CareerMovement>
    fun findAllByTenantIdOrderByEffectiveDateDesc(tenantId: UUID): List<CareerMovement>
    fun findByIdAndTenantId(id: UUID, tenantId: UUID): CareerMovement?
    fun findByTenantIdAndMovementNumber(tenantId: UUID, movementNumber: String): CareerMovement?
    fun countByTenantId(tenantId: UUID): Long
}

@Repository
interface EmployeeSalaryHistoryRepository : JpaRepository<EmployeeSalaryHistory, UUID> {
    fun findAllByTenantIdAndEmployeeIdOrderByEffectiveFromDesc(tenantId: UUID, employeeId: UUID): List<EmployeeSalaryHistory>
    fun findByTenantIdAndEmployeeIdAndIsCurrentTrue(tenantId: UUID, employeeId: UUID): EmployeeSalaryHistory?
    fun findByTenantIdAndCareerMovementId(tenantId: UUID, careerMovementId: UUID): EmployeeSalaryHistory?
}
