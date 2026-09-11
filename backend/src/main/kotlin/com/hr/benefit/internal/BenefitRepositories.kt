package com.hr.benefit.internal

import com.hr.benefit.BenefitClaimStatus
import com.hr.benefit.EnrollmentStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface BenefitCategoryRepository : JpaRepository<BenefitCategory, UUID> {
    fun findAllByIsActiveTrueOrderByCodeAsc(): List<BenefitCategory>
    fun findByCode(code: String): BenefitCategory?
}

@Repository
interface BenefitPolicyRepository : JpaRepository<BenefitPolicy, UUID> {
    fun findAllByIsActiveTrueOrderByCodeAsc(): List<BenefitPolicy>
    fun findAllByCategoryIdAndIsActiveTrue(categoryId: UUID): List<BenefitPolicy>
    fun findByCode(code: String): BenefitPolicy?
}

@Repository
interface EmployeeBenefitEnrollmentRepository : JpaRepository<EmployeeBenefitEnrollment, UUID> {
    fun findAllByEmployeeIdAndStatusOrderByStartDateDesc(employeeId: UUID, status: EnrollmentStatus): List<EmployeeBenefitEnrollment>
    fun findAllByEmployeeIdOrderByStartDateDesc(employeeId: UUID): List<EmployeeBenefitEnrollment>
    fun findByIdAndEmployeeId(id: UUID, employeeId: UUID): EmployeeBenefitEnrollment?
}

@Repository
interface BenefitDependentRepository : JpaRepository<BenefitDependent, UUID> {
    fun findAllByEnrollmentIdAndIsCoveredTrue(enrollmentId: UUID): List<BenefitDependent>
    fun findAllByEnrollmentId(enrollmentId: UUID): List<BenefitDependent>
    fun findByIdAndEnrollmentId(id: UUID, enrollmentId: UUID): BenefitDependent?
}

@Repository
interface BenefitClaimRepository : JpaRepository<BenefitClaim, UUID> {
    fun findAllByEmployeeIdOrderByClaimDateDesc(employeeId: UUID): List<BenefitClaim>
    fun findAllByEmployeeIdAndStatusOrderByClaimDateDesc(employeeId: UUID, status: BenefitClaimStatus): List<BenefitClaim>
    fun findByIdAndEmployeeId(id: UUID, employeeId: UUID): BenefitClaim?
    fun countByEmployeeIdAndStatus(employeeId: UUID, status: BenefitClaimStatus): Long
    fun findAllByEnrollmentId(enrollmentId: UUID): List<BenefitClaim>
}
